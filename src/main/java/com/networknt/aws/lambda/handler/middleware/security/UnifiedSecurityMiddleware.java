package com.networknt.aws.lambda.handler.middleware.security;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.MapUtil;
import com.networknt.basicauth.BasicAuthConfig;
import com.networknt.config.Config;
import com.networknt.openapi.UnifiedPathPrefixAuth;
import com.networknt.openapi.UnifiedSecurityConfig;
import com.networknt.security.SecurityConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UnifiedSecurityMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UnifiedSecurityMiddleware.class);
    static final String BEARER_PREFIX = "BEARER";
    static final String BASIC_PREFIX = "BASIC";
    static final String API_KEY = "apikey";
    static final String JWT = "jwt";
    static final String SWT = "swt";
    static final String MISSING_AUTH_TOKEN = "ERR10002";
    static final String INVALID_AUTHORIZATION_HEADER = "ERR12003";
    static final String HANDLER_NOT_FOUND = "ERR11200";
    static final String MISSING_PATH_PREFIX_AUTH = "ERR10078";

    private static UnifiedSecurityConfig CONFIG;

    public UnifiedSecurityMiddleware() {
        CONFIG = UnifiedSecurityConfig.load();
        if(LOG.isInfoEnabled()) LOG.info("UnifiedSecurityMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if (LOG.isDebugEnabled()) LOG.debug("UnifiedSecurityMiddleware.execute starts.");
        // need to skip this handler if the response is set by the router handler.
        if (exchange.isRequestComplete()) {
            if (LOG.isTraceEnabled())
                LOG.trace("UnifiedSecurityMiddleware.execute skips as the response is already set.");
            return successMiddlewareStatus();
        }
        String reqPath = exchange.getRequest().getPath();
        // check if the path prefix is in the anonymousPrefixes list. If yes, skip all other check and goes to next handler.
        if (CONFIG.getAnonymousPrefixes() != null && CONFIG.getAnonymousPrefixes().stream().anyMatch(reqPath::startsWith)) {
            if(LOG.isTraceEnabled()) LOG.trace("Skip request path base on anonymousPrefixes for " + reqPath);
            return successMiddlewareStatus();
        }
        if(CONFIG.getPathPrefixAuths() != null) {
            boolean found = false;
            // iterate each entry to check enabled security methods.
            for(UnifiedPathPrefixAuth pathPrefixAuth: CONFIG.getPathPrefixAuths()) {
                if(LOG.isTraceEnabled())
                    LOG.trace("Check with requestPath = {} prefix = {}", reqPath, pathPrefixAuth.getPrefix());
                if(reqPath.startsWith(pathPrefixAuth.getPrefix())) {
                    found = true;
                    if(LOG.isTraceEnabled()) LOG.trace("Found with requestPath = " + reqPath + " prefix = " + pathPrefixAuth.getPrefix());
                    // check jwt and basic first with authorization header, then check the apikey if it is enabled.
                    if(pathPrefixAuth.isBasic() || pathPrefixAuth.isJwt() || pathPrefixAuth.isSwt()) {
                        Optional<String> optionalAuth = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.AUTHORIZATION);
                        if(optionalAuth.isEmpty()) {
                            LOG.error("Basic or JWT or SWT is enabled and authorization header is missing.");
                            // set the WWW-Authenticate header to Basic realm="realm"
                            if(pathPrefixAuth.isBasic()) {
                                if(LOG.isTraceEnabled()) LOG.trace("Basic is enabled and set WWW-Authenticate header to Basic realm=\"Default Realm\"");
                                Status status = new Status(MISSING_AUTH_TOKEN);
                                var responseEvent = new APIGatewayProxyResponseEvent();
                                var headers = new HashMap<String, String>();
                                headers.put(HeaderKey.WWW_AUTHENTICATE, "Basic realm=\"Default Realm\"");
                                responseEvent.setHeaders(headers);
                                responseEvent.setStatusCode(status.getStatusCode());
                                responseEvent.setBody(status.toString());
                                exchange.setInitialResponse(responseEvent);
                                if(LOG.isDebugEnabled()) LOG.debug("UnifiedSecurityMiddleware.execute ends with an error.");
                                return status;
                            }
                            if(LOG.isDebugEnabled()) LOG.debug("UnifiedSecurityMiddleware.execute ends with an error.");
                            return new Status(MISSING_AUTH_TOKEN);
                        } else {
                            String authorization = optionalAuth.get();
                            // make sure that the length is greater than 5.
                            if(authorization.trim().length() <= 5) {
                                LOG.error("Invalid/Unsupported authorization header {}", authorization);
                                return new Status(INVALID_AUTHORIZATION_HEADER, authorization);
                            }
                            // check if it is basic or bearer and handler it differently.
                            if(BASIC_PREFIX.equalsIgnoreCase(authorization.substring(0, 5))) {
                                Map<String, LambdaHandler> handlers = Handler.getHandlers();
                                BasicAuthMiddleware handler = (BasicAuthMiddleware) handlers.get(BASIC_PREFIX.toLowerCase());
                                if(handler == null) {
                                    LOG.error("Cannot find BasicAuthMiddleware with alias name basic.");
                                    return new Status(HANDLER_NOT_FOUND, "com.networknt.aws.lambda.handler.middleware.security.BasicAuthMiddleware@basic");
                                } else {
                                    // if the handler is not enabled in the configuration, break here to call next handler.
                                    if(!handler.isEnabled()) {
                                        break;
                                    }
                                    return handler.handleBasicAuth(exchange, reqPath, authorization);
                                }
                            } else if (BEARER_PREFIX.equalsIgnoreCase(authorization.substring(0, 6))) {
                                // in the case that a bearer token is used, there are three scenarios: both jwt and swt are true, only jwt is true and only swt is true
                                // in the first case, we need to identify if the token is jwt or swt before calling the right handler to verify it.
                                Map<String, LambdaHandler> handlers = Handler.getHandlers();
                                if(pathPrefixAuth.isJwt() && pathPrefixAuth.isSwt()) {
                                    // both jwt and swt are enabled.
                                    boolean isJwt = StringUtils.isJwtToken(authorization);
                                    if(LOG.isTraceEnabled()) LOG.trace("Both jwt and swt are true and check token is jwt = {}", isJwt);
                                    if(isJwt) {
                                        JwtVerifyMiddleware handler = (JwtVerifyMiddleware) handlers.get(JWT);
                                        if (handler == null) {
                                            LOG.error("Cannot find JwtVerifyMiddleware with alias name jwt.");
                                            return new Status(HANDLER_NOT_FOUND, "com.networknt.aws.lambda.handler.middleware.security.JwtVerifyMiddleware@jwt");
                                        } else {
                                            // if the handler is not enabled in the configuration, break here to call next handler.
                                            if(!handler.isEnabled()) {
                                                break;
                                            }
                                            // get the jwkServiceIds list.
                                            return handler.handleJwt(exchange, pathPrefixAuth.getPrefix(), reqPath, pathPrefixAuth.getJwkServiceIds());
                                        }
                                    } else {
                                        SwtVerifyMiddleware handler = (SwtVerifyMiddleware) handlers.get(SWT);
                                        if (handler == null) {
                                            LOG.error("Cannot find SwtVerifyMiddleware with alias name swt.");
                                            return new Status(HANDLER_NOT_FOUND, "com.networknt.aws.lambda.handler.middleware.security.SwtVerifyMiddleware@swt");
                                        } else {
                                            // if the handler is not enabled in the configuration, break here to call next handler.
                                            if(!handler.isEnabled()) {
                                                break;
                                            }
                                            // get the jwkServiceIds list.
                                            return handler.handleSwt(exchange, reqPath, pathPrefixAuth.getSwtServiceIds());
                                        }
                                    }
                                } else if(pathPrefixAuth.isJwt()) {
                                    // only jwt is enabled
                                    JwtVerifyMiddleware handler = (JwtVerifyMiddleware) handlers.get(JWT);
                                    if (handler == null) {
                                        LOG.error("Cannot find JwtVerifyMiddleware with alias name jwt.");
                                        return new Status(HANDLER_NOT_FOUND, "com.networknt.aws.lambda.handler.middleware.security.JwtVerifyMiddleware@jwt");
                                    } else {
                                        // if the handler is not enabled in the configuration, break here to call next handler.
                                        if(!handler.isEnabled()) {
                                            break;
                                        }
                                        // get the jwkServiceIds list.
                                        return handler.handleJwt(exchange, pathPrefixAuth.getPrefix(), reqPath, pathPrefixAuth.getJwkServiceIds());
                                    }
                                } else {
                                    // only swt is enabled
                                    SwtVerifyMiddleware handler = (SwtVerifyMiddleware) handlers.get(SWT);
                                    if (handler == null) {
                                        LOG.error("Cannot find SwtVerifyMiddleware with alias name swt.");
                                        return new Status(HANDLER_NOT_FOUND, "com.networknt.aws.lambda.handler.middleware.security.SwtVerifyMiddleware@swt");
                                    } else {
                                        // if the handler is not enabled in the configuration, break here to call next handler.
                                        if(!handler.isEnabled()) {
                                            break;
                                        }
                                        // get the jwkServiceIds list.
                                        return handler.handleSwt(exchange, reqPath, pathPrefixAuth.getSwtServiceIds());
                                    }
                                }
                            } else {
                                String s = authorization.length() > 10 ? authorization.substring(0, 10) : authorization;
                                LOG.error("Invalid/Unsupported authorization header {}", s);
                                return new Status(INVALID_AUTHORIZATION_HEADER, s);
                            }
                        }
                    } else if (pathPrefixAuth.isApikey()) {
                        Map<String, LambdaHandler> handlers = Handler.getHandlers();
                        ApiKeyMiddleware handler = (ApiKeyMiddleware) handlers.get(API_KEY);
                        if(handler == null) {
                            LOG.error("Cannot find ApiKeyMiddleware with alias name apikey.");
                            return new Status(HANDLER_NOT_FOUND, "com.networknt.aws.lambda.handler.middleware.security.ApiKeyMiddleware@apikey");
                        } else {
                            // if the handler is not enabled in the configuration, break here to call next handler.
                            if(!handler.isEnabled()) {
                                break;
                            }
                            return handler.handleApiKey(exchange, reqPath);
                        }
                    }
                }
            }
            if(!found) {
                // cannot find the prefix auth entry for request path.
                LOG.error("Cannot find prefix entry in pathPrefixAuths for {}", reqPath);
                return new Status(MISSING_PATH_PREFIX_AUTH, reqPath);
            }
        } else {
            // pathPrefixAuths is not defined in the values.yml
            LOG.error("Cannot find pathPrefixAuths definition for {}", reqPath);
            return new Status(MISSING_PATH_PREFIX_AUTH, reqPath);
        }

        if(LOG.isDebugEnabled()) LOG.debug("UnifiedSecurityMiddleware.execute ends.");
        return successMiddlewareStatus();
    }

    @Override
    public void getCachedConfigurations() {

    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                UnifiedSecurityConfig.CONFIG_NAME,
                UnifiedSecurityMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(UnifiedSecurityConfig.CONFIG_NAME), null);
    }

    @Override
    public void reload() {

    }

    @Override
    public boolean isContinueOnFailure() {
        return false;
    }

    @Override
    public boolean isAudited() {
        return false;
    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }

}
