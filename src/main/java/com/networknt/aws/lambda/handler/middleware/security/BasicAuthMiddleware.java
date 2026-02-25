package com.networknt.aws.lambda.handler.middleware.security;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.utility.MapUtil;
import com.networknt.basicauth.BasicAuthConfig;
import com.networknt.basicauth.UserAuth;
import com.networknt.config.Config;
import com.networknt.ldap.LdapUtil;
import com.networknt.status.Status;
import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BasicAuthMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthMiddleware.class);
    static final String BEARER_PREFIX = "BEARER";
    static final String BASIC_PREFIX = "BASIC";

    static final String MISSING_AUTH_TOKEN = "ERR10002";
    static final String INVALID_BASIC_HEADER = "ERR10046";
    static final String INVALID_USERNAME_OR_PASSWORD = "ERR10047";
    static final String NOT_AUTHORIZED_REQUEST_PATH = "ERR10071";
    static final String INVALID_AUTHORIZATION_HEADER = "ERR12003";
    static final String BEARER_USER_NOT_FOUND = "ERR10072";

    private volatile String configName = BasicAuthConfig.CONFIG_NAME;

    public BasicAuthMiddleware() {
        if(LOG.isTraceEnabled()) LOG.trace("BasicAuthMiddleware is loaded.");
    }

    /**
     * Please note that this constructor is only for testing to load different config files
     * to test different configurations.
     * @param configName String
     */
    @Deprecated
    public BasicAuthMiddleware(String configName) {
        this.configName = configName;
        if(LOG.isInfoEnabled()) LOG.info("BasicAuthMiddleware is loaded.");
    }


    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute starts.");
        Optional<String> optionalAuth = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.AUTHORIZATION);
        String requestPath = exchange.getRequest().getPath();
        BasicAuthConfig config = BasicAuthConfig.load(configName);
        if (optionalAuth.isEmpty()) {
            /* no auth header */
            return this.handleAnonymousAuth(exchange, requestPath, config);
        } else {
            /* contains auth header */
            String auth = optionalAuth.get();
            if(auth.trim().isEmpty()) {
                return this.handleAnonymousAuth(exchange, requestPath, config);
            }
            // verify the header with the config file. assuming it is basic authentication first.
            if (BASIC_PREFIX.equalsIgnoreCase(auth.substring(0, 5))) {
                // check if the length is greater than 6 for issue1513
                if(auth.trim().length() == 5) {
                    LOG.error("Invalid/Unsupported authorization header {}", auth);
                    return new Status(INVALID_AUTHORIZATION_HEADER, auth);
                } else {
                    return this.handleBasicAuth(exchange, requestPath, auth);
                }
            } else if (BEARER_PREFIX.equalsIgnoreCase(auth.substring(0, 6))) {
                return this.handleBearerToken(exchange, requestPath, auth, config);
            } else {
                LOG.error("Invalid/Unsupported authorization header {}", auth.substring(0, 10));
                return new Status(INVALID_AUTHORIZATION_HEADER, auth.substring(0, 10));
            }
        }
    }

    /**
     * Handle anonymous authentication.
     * If requests are anonymous and do not have a path entry, we block the request.
     *
     * @param exchange - current exchange.
     * @param requestPath - path for current request.
     * @return success status if there is no error. Otherwise, an error status is returned.
     */
    private Status handleAnonymousAuth(LightLambdaExchange exchange, String requestPath, BasicAuthConfig config) {
        if (config.isAllowAnonymous() && config.getUsers().containsKey(BasicAuthConfig.ANONYMOUS)) {
            List<String> paths = config.getUsers().get(BasicAuthConfig.ANONYMOUS).getPaths();
            boolean match = false;
            for (String path : paths) {
                if (requestPath.startsWith(path)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                LOG.error("Request path '{}' is not authorized for user '{}'", requestPath, BasicAuthConfig.ANONYMOUS);
                Status status = new Status(NOT_AUTHORIZED_REQUEST_PATH, requestPath, BasicAuthConfig.ANONYMOUS);
                // this is to handler the client with pre-emptive authentication with response code 401
                var responseEvent = new APIGatewayProxyResponseEvent();
                var headers = new HashMap<String, String>();
                headers.put(HeaderKey.WWW_AUTHENTICATE, "Basic realm=\"Default Realm\"");
                responseEvent.setHeaders(headers);
                responseEvent.setStatusCode(status.getStatusCode());
                responseEvent.setBody(status.toString());
                exchange.setInitialResponse(responseEvent);
                if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                return status;
            }
        } else {
            LOG.error("Anonymous is not allowed and authorization header is missing.");
            Status status = new Status(MISSING_AUTH_TOKEN);
            // this is to handler the client with pre-emptive authentication with response code 401
            var responseEvent = new APIGatewayProxyResponseEvent();
            var headers = new HashMap<String, String>();
            headers.put(HeaderKey.WWW_AUTHENTICATE, "Basic realm=\"Basic Auth\"");
            responseEvent.setHeaders(headers);
            responseEvent.setStatusCode(status.getStatusCode());
            responseEvent.setBody(status.toString());
            exchange.setInitialResponse(responseEvent);
            if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
            return status;
        }
        return successMiddlewareStatus();
    }

    /**
     * Handle basic authentication header.
     * If the request coming in has an incorrect format for basic auth, we block the request.
     * We also block the request if the path is not configured to have basic authentication.
     *
     * @param exchange - current exchange.
     * @param requestPath - path found within current request.
     * @param auth - auth string
     * @return Status to indicate if an error or success.
     */
    public Status handleBasicAuth(LightLambdaExchange exchange, String requestPath, String auth) {
        BasicAuthConfig config = BasicAuthConfig.load(configName);
        String credentials = auth.substring(6);
        int pos = credentials.indexOf(':');
        if (pos == -1) {
            credentials = new String(org.apache.commons.codec.binary.Base64.decodeBase64(credentials), UTF_8);
        }
        pos = credentials.indexOf(':');
        if (pos != -1) {
            String username = credentials.substring(0, pos);
            String password = credentials.substring(pos + 1);
            if(LOG.isTraceEnabled()) LOG.trace("input username = {}, password = {}", username, StringUtils.maskHalfString(password));
            UserAuth user = config.getUsers().get(username);
            // if user cannot be found in the config, return immediately.
            if (user == null) {
                LOG.error("User '{}' is not found in the configuration file.", username);
                if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                return new Status(INVALID_USERNAME_OR_PASSWORD);
            }
            // At this point, we know the user is found in the config file.
            if (username.equals(user.getUsername())
                    && StringUtils.isEmpty(user.getPassword())
                    && config.isEnableAD()) {
                // Call LdapUtil with LDAP authentication and authorization given user is matched, password is empty, and AD is enabled.
                if(LOG.isTraceEnabled()) LOG.trace("Call LdapUtil with LDAP authentication and authorization for user = {}", username);
                if (!handleLdapAuth(user, password)) {
                    if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                    return new Status(INVALID_USERNAME_OR_PASSWORD);
                }
            } else {
                if(LOG.isTraceEnabled()) LOG.trace("Validate basic auth based on config username {} and password {}", user.getUsername(), StringUtils.maskHalfString(user.getPassword()));
                // if username matches config, password matches config, and path matches config, pass
                if (!(user.getUsername().equals(username)
                        && password.equals(user.getPassword()))) {
                    LOG.error("Invalid username or password with authorization header = {}", StringUtils.maskHalfString(auth));
                    if (LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                    return new Status(INVALID_USERNAME_OR_PASSWORD);
                }
            }
            // Here we have passed the authentication. Let's do the authorization with the paths.
            if(LOG.isTraceEnabled()) LOG.trace("Username and password validation is done for user = {}", username);
            boolean match = false;
            for (String path : user.getPaths()) {
                if (requestPath.startsWith(path)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                LOG.error("Request path '{}' is not authorized for user '{}", requestPath, user.getUsername());
                if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                return new Status(NOT_AUTHORIZED_REQUEST_PATH, requestPath, user.getUsername());
            }
        } else {
            LOG.error("Invalid basic authentication header. It must be username:password base64 encode.");
            if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
            return new Status(INVALID_BASIC_HEADER, auth.substring(0, 10));
        }
        return successMiddlewareStatus();
    }

    /**
     * Handle LDAP authentication and authorization
     * @param user
     * @return true if Ldap auth success, false if Ldap auth failure
     */
    private static boolean handleLdapAuth(UserAuth user, String password) {
        boolean isAuthenticated = LdapUtil.authenticate(user.getUsername(), password);
        if (!isAuthenticated) {
            LOG.error("user '" + user.getUsername() + "' Ldap authentication failed");
            return false;
        }
        return true;
    }

    /**
     * Handle Bearer token authentication.
     * We block requests that are not configured to have bearer tokens.
     * We also block requests that are configured to have a bearer token
     *
     * @param exchange - current exchange.
     * @param requestPath - path for request
     * @param auth - auth string
     * @return Status to indicate if an error or success.
     */
    private Status handleBearerToken(LightLambdaExchange exchange, String requestPath, String auth, BasicAuthConfig config) {
        // not basic token. check if the OAuth 2.0 bearer token is allowed.
        if (!config.isAllowBearerToken()) {
            LOG.error("Not a basic authentication header, and bearer token is not allowed.");
            if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
            return new Status(INVALID_BASIC_HEADER, auth.substring(0, 10));
        } else {
            // bearer token is allowed, we need to validate it and check the allowed paths.
            UserAuth user = config.getUsers().get(BasicAuthConfig.BEARER);
            if (user != null) {
                // check the path for authorization
                List<String> paths = user.getPaths();
                boolean match = false;
                for (String path : paths) {
                    if (requestPath.startsWith(path)) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    LOG.error("Request path '{}' is not authorized for user '{}' ", requestPath, BasicAuthConfig.BEARER);
                    if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                    return new Status(NOT_AUTHORIZED_REQUEST_PATH, requestPath, BasicAuthConfig.BEARER);
                }
            } else {
                LOG.error("Bearer token is allowed but missing the bearer user path definitions for authorization");
                if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                return new Status(BEARER_USER_NOT_FOUND);
            }
        }
        return successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return BasicAuthConfig.load(configName).isEnabled();
    }
}
