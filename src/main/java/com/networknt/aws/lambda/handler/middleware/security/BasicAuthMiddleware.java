package com.networknt.aws.lambda.handler.middleware.security;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.utility.MapUtil;
import com.networknt.basicauth.BasicAuthConfig;
import com.networknt.basicauth.UserAuth;
import com.networknt.ldap.LdapUtil;
import com.networknt.status.Status;
import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
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

    private final BasicAuthConfig config;

    public BasicAuthMiddleware() {
        this.config = BasicAuthConfig.load();
        LOG.info("BasicAuthMiddleware is constructed.");
    }

    /**
     * Please note that this constructor is only for testing to load different config files
     * to test different configurations.
     * @param configName String
     */
    @Deprecated
    public BasicAuthMiddleware(String configName) {
        this.config = BasicAuthConfig.load(configName);
        LOG.info("BasicAuthMiddleware is constructed.");
    }


    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute starts.");
        Optional<String> optionalAuth = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.AUTHORIZATION);
        String requestPath = exchange.getRequest().getPath();

        if (optionalAuth.isEmpty()) {
            /* no auth header */
            return this.handleAnonymousAuth(exchange, requestPath, config);
        } else {
            /* contains auth header */
            String auth = optionalAuth.get();
            if(auth.trim().isEmpty()) {
                return this.handleAnonymousAuth(exchange, requestPath, config);
            }
            AuthorizationScheme scheme = AuthorizationScheme.parse(auth);
            // A bare scheme must not reach handleBearerToken, which only checks paths.
            if (scheme != AuthorizationScheme.UNKNOWN && !AuthorizationScheme.hasCredentials(auth, scheme)) {
                LOG.error("Invalid/Unsupported authorization header.");
                return new Status(INVALID_AUTHORIZATION_HEADER, scheme.name());
            }
            if (scheme == AuthorizationScheme.BASIC) {
                return this.handleBasicAuth(exchange, requestPath, auth);
            } else if (scheme == AuthorizationScheme.BEARER) {
                return this.handleBearerToken(exchange, requestPath, auth, config);
            }
            LOG.error("Invalid/Unsupported authorization header.");
            return new Status(INVALID_AUTHORIZATION_HEADER, "unknown");
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
                responseEvent.setIsBase64Encoded(false);
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
            responseEvent.setIsBase64Encoded(false);
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
        if (!AuthorizationScheme.hasCredentials(auth, AuthorizationScheme.BASIC)) {
            LOG.error("Invalid/Unsupported Basic authorization header.");
            return new Status(INVALID_AUTHORIZATION_HEADER, BASIC_PREFIX);
        }
        String credentials = auth.substring(6);
        int pos = credentials.indexOf(':');
        if (pos == -1) {
            credentials = new String(org.apache.commons.codec.binary.Base64.decodeBase64(credentials), UTF_8);
        }
        pos = credentials.indexOf(':');
        if (pos != -1) {
            String username = credentials.substring(0, pos);
            String password = credentials.substring(pos + 1);
            UserAuth user = config.getUsers().get(username);
            // Reserved users describe authorization policies, never Basic identities.
            if (user == null || StringUtils.isEmpty(password)
                    || BasicAuthConfig.ANONYMOUS.equals(username) || BasicAuthConfig.BEARER.equals(username)) {
                LOG.error("Invalid Basic username or password.");
                if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                return new Status(INVALID_USERNAME_OR_PASSWORD);
            }
            if (StringUtils.isEmpty(user.getPassword()) && config.isEnableAD()) {
                // Delegate configured identities without a local password to LDAP.
                if(LOG.isTraceEnabled()) LOG.trace("Call LdapUtil for Basic authentication.");
                if (!handleLdapAuth(user, password)) {
                    if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                    return new Status(INVALID_USERNAME_OR_PASSWORD);
                }
            } else {
                // Compare the configured local password without early character mismatches.
                if (!(user.getPassword() != null
                        && MessageDigest.isEqual(password.getBytes(UTF_8), user.getPassword().getBytes(UTF_8)))) {
                    LOG.error("Invalid Basic username or password.");
                    if (LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                    return new Status(INVALID_USERNAME_OR_PASSWORD);
                }
            }
            // Here we have passed the authentication. Let's do the authorization with the paths.
            if(LOG.isTraceEnabled()) LOG.trace("Basic username and password validation is done.");
            boolean match = false;
            for (String path : user.getPaths()) {
                if (requestPath.startsWith(path)) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                String safePath = requestPath.replace('\r', ' ').replace('\n', ' ');
                LOG.error("Request path '{}' is not authorized for user '{}'", safePath, user.getUsername());
                if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
                return new Status(NOT_AUTHORIZED_REQUEST_PATH, safePath, user.getUsername());
            }
        } else {
            LOG.error("Invalid basic authentication header. It must be username:password base64 encode.");
            if(LOG.isDebugEnabled()) LOG.debug("BasicAuthMiddleware.execute ends with an error.");
            return new Status(INVALID_BASIC_HEADER, BASIC_PREFIX);
        }
        return successMiddlewareStatus();
    }

    /**
     * Handle LDAP authentication and authorization
     * @param user
     * @return true if Ldap auth success, false if Ldap auth failure
     */
    protected boolean handleLdapAuth(UserAuth user, String password) {
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
            return new Status(INVALID_BASIC_HEADER, BEARER_PREFIX);
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
        return config.isEnabled();
    }
}
