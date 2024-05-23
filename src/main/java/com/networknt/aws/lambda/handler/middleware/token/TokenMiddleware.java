package com.networknt.aws.lambda.handler.middleware.token;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.MapUtil;
import com.networknt.cache.CacheManager;
import com.networknt.client.ClientConfig;
import com.networknt.client.oauth.Jwt;
import com.networknt.client.oauth.OauthHelper;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.monad.Result;
import com.networknt.monad.Success;
import com.networknt.router.middleware.TokenConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * This middleware is used to get the jwt token for the client credential grant type and put it into the Authorization
 * header when the backend lambda function is invoking another service. The token is cached in the cache manager and
 * it only retrieves a new one before the current one is expired.
 *
 * This is to support the east west security between the services in the light lambda native platform. The middleware
 * will check if service_id header is in the request. Normally, this id should map to an ALB or API Gateway endpoint
 * that is used to call another Lambda function or a service running in a container. When service_url is in the header,
 * it is going to be used to invoke the downstream API, otherwise, the downstream URL will be discovered from the config.
 *
 */
public class TokenMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TokenMiddleware.class);
    private static final String HANDLER_DEPENDENCY_ERROR = "ERR10074";
    private static TokenConfig CONFIG;
    private static final String TOKEN = "token";

    private static final CacheManager cacheManager = CacheManager.getInstance();

    public TokenMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("TokenMiddleware is constructed");
        CONFIG = TokenConfig.load();
    }

    /**
     * This constructor should only be used for testing. Hence, it is marked as deprecated.
     * @param cfg
     */
    @Deprecated
    public TokenMiddleware(TokenConfig cfg) {
        if (LOG.isInfoEnabled()) LOG.info("TokenMiddleware is constructed");
        CONFIG = cfg;
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        // This handler must be put after the prefix or dict handler so that the serviceId is
        // readily available in the header resolved by the path or the endpoint from the request.
        if(LOG.isDebugEnabled()) LOG.debug("TokenMiddleware.execute starts.");
        // get the service_url from the header to determine if the request needs to be handled.
        Optional<String> optionalServiceId = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.SERVICE_ID);
        if(optionalServiceId.isEmpty()) {
            if(LOG.isDebugEnabled()) LOG.debug("TokenMiddleware.execute ends. The service_id is not in the header.");
            return successMiddlewareStatus();
        }
        String requestPath = exchange.getRequest().getPath();
        // this handler will only work with a list of applied path prefixes in the token.yml config file.
        if (CONFIG.getAppliedPathPrefixes() != null && CONFIG.getAppliedPathPrefixes().stream().anyMatch(s -> requestPath.startsWith(s))) {
            String serviceId = optionalServiceId.get();
            Result<Jwt> result = getJwtToken(serviceId);
            if(result.isFailure()) {
                LOG.error("Cannot populate or renew jwt for client credential grant type: {}", result.getError().toString());
                if(LOG.isDebugEnabled()) LOG.debug("TokenMiddleware.execute ends with an error.");
                return result.getError();
            } else {
                Jwt cachedJwt = result.getResult();
                // check if there is a bear token in the authorization header in the request. If there
                // is one, then this must be the subject token that is linked to the original user.
                // We will keep this token in the Authorization header but create a new token with
                // client credentials grant type with scopes for the particular client. (Can we just
                // assume that the subject token has the scope already?)
                Optional<String> optionalToken = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.AUTHORIZATION);
                if(optionalToken.isEmpty()) {
                    if(LOG.isTraceEnabled())
                        LOG.trace("Adding jwt token to Authorization header with Bearer {}", cachedJwt.getJwt().substring(0, 20));
                    exchange.getRequest().getHeaders().put(HeaderKey.AUTHORIZATION, "Bearer " + cachedJwt.getJwt());
                } else {
                    String token = optionalToken.get();
                    if(LOG.isTraceEnabled()) {
                        LOG.trace("Authorization header is used with {}", token.length() > 10 ? token.substring(0, 10) : token); // it could be "Basic "
                        LOG.trace("Adding jwt token to X-Scope-Token header with Bearer {}", cachedJwt.getJwt().substring(0, 20));
                    }
                    exchange.getRequest().getHeaders().put(HeaderKey.SCOPE_TOKEN, "Bearer " + cachedJwt.getJwt());
                }
            }
        }
        if(LOG.isDebugEnabled()) LOG.debug("TokenMiddleware.execute ends.");
        return successMiddlewareStatus();
    }


    public static Result<Jwt> getJwtToken(String serviceId) {
        ClientConfig clientConfig = ClientConfig.get();
        Map<String, Object> tokenConfig = clientConfig.getTokenConfig();
        Map<String, Object> ccConfig = (Map<String, Object>)tokenConfig.get(ClientConfig.CLIENT_CREDENTIALS);
        Result<Jwt> result;
        // get the jwt token from the cache.
        Jwt cachedJwt = null;
        if(cacheManager != null) {
            if(LOG.isTraceEnabled()) LOG.trace("Get jwt token from cache for serviceId: {}", serviceId);
            String cachedJwtString = (String) cacheManager.get(TOKEN, serviceId);
            if(cachedJwtString != null && !cachedJwtString.isEmpty()) {
                if(LOG.isTraceEnabled()) LOG.trace("Cached jwt token: {}", cachedJwtString);
                cachedJwt = JsonMapper.fromJson(cachedJwtString, Jwt.class);
            }
        }
        // get a new token if cachedJwt is null or the jwt is about expired.
        if(cachedJwt == null || cachedJwt.getExpire() - Long.valueOf((Integer)tokenConfig.get(ClientConfig.TOKEN_RENEW_BEFORE_EXPIRED)) < System.currentTimeMillis()) {
            Jwt.Key key = new Jwt.Key(serviceId);
            cachedJwt = new Jwt(key); // create a new instance if the cache is empty for the serviceId.

            if(clientConfig.isMultipleAuthServers()) {
                // get the right client credentials configuration based on the serviceId
                Map<String, Object> serviceIdAuthServers = ClientConfig.getServiceIdAuthServers(ccConfig.get(ClientConfig.SERVICE_ID_AUTH_SERVERS));
                if(serviceIdAuthServers == null) {
                    throw new RuntimeException("serviceIdAuthServers property is missing in the token client credentials configuration");
                }
                Map<String, Object> authServerConfig = (Map<String, Object>)serviceIdAuthServers.get(serviceId);
                // overwrite some elements in the auth server config if it is not defined.
                if(authServerConfig.get(ClientConfig.PROXY_HOST) == null) authServerConfig.put(ClientConfig.PROXY_HOST, tokenConfig.get(ClientConfig.PROXY_HOST));
                if(authServerConfig.get(ClientConfig.PROXY_PORT) == null) authServerConfig.put(ClientConfig.PROXY_PORT, tokenConfig.get(ClientConfig.PROXY_PORT));
                if(authServerConfig.get(ClientConfig.TOKEN_RENEW_BEFORE_EXPIRED) == null) authServerConfig.put(ClientConfig.TOKEN_RENEW_BEFORE_EXPIRED, tokenConfig.get(ClientConfig.TOKEN_RENEW_BEFORE_EXPIRED));
                if(authServerConfig.get(ClientConfig.EXPIRED_REFRESH_RETRY_DELAY) == null) authServerConfig.put(ClientConfig.EXPIRED_REFRESH_RETRY_DELAY, tokenConfig.get(ClientConfig.EXPIRED_REFRESH_RETRY_DELAY));
                if(authServerConfig.get(ClientConfig.EARLY_REFRESH_RETRY_DELAY) == null) authServerConfig.put(ClientConfig.EARLY_REFRESH_RETRY_DELAY, tokenConfig.get(ClientConfig.EARLY_REFRESH_RETRY_DELAY));
                cachedJwt.setCcConfig(authServerConfig);
            } else {
                // only one client credentials configuration, populate some common elements to the ccConfig from tokenConfig.
                ccConfig.put(ClientConfig.PROXY_HOST, tokenConfig.get(ClientConfig.PROXY_HOST));
                ccConfig.put(ClientConfig.PROXY_PORT, tokenConfig.get(ClientConfig.PROXY_PORT));
                ccConfig.put(ClientConfig.TOKEN_RENEW_BEFORE_EXPIRED, tokenConfig.get(ClientConfig.TOKEN_RENEW_BEFORE_EXPIRED));
                ccConfig.put(ClientConfig.EXPIRED_REFRESH_RETRY_DELAY, tokenConfig.get(ClientConfig.EXPIRED_REFRESH_RETRY_DELAY));
                ccConfig.put(ClientConfig.EARLY_REFRESH_RETRY_DELAY, tokenConfig.get(ClientConfig.EARLY_REFRESH_RETRY_DELAY));
                cachedJwt.setCcConfig(ccConfig);
            }
            result = OauthHelper.populateCCToken(cachedJwt);
            if(result.isSuccess()) {
                // put the cachedJwt to the cache.
                if(cacheManager != null) cacheManager.put(TOKEN, serviceId, JsonMapper.toJson(cachedJwt));
            }
        } else {
            // the cached jwt is not null and still valid.
            result = Success.of(cachedJwt);
        }
        return result;
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
    public void getCachedConfigurations() {

    }


    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                TokenConfig.CONFIG_NAME,
                TokenMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(TokenConfig.CONFIG_NAME),
                null
        );

    }

    @Override
    public void reload() {

    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }
}
