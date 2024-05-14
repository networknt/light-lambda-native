package com.networknt.aws.lambda.handler.middleware.security;

import com.networknt.aws.lambda.app.LambdaApp;
import com.networknt.cache.CacheManager;
import com.networknt.client.ClientConfig;
import com.networknt.client.oauth.OauthHelper;
import com.networknt.client.oauth.TokenKeyRequest;
import com.networknt.config.ConfigException;
import com.networknt.config.JsonMapper;
import com.networknt.exception.ClientException;
import com.networknt.exception.ExpiredTokenException;
import com.networknt.security.SecurityConfig;
import com.networknt.status.Status;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.keys.resolvers.JwksVerificationKeyResolver;
import org.jose4j.keys.resolvers.VerificationKeyResolver;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.*;
import org.jose4j.jwx.JsonWebStructure;

import java.util.*;
import java.util.function.BiFunction;

public class JwtVerifier extends TokenVerifier {
    private static final Logger logger = LoggerFactory.getLogger(JwtVerifier.class);
    static final String GET_KEY_ERROR = "ERR10066";
    private static Map<String, JwtClaims> cache;
    public static final String JWT = "jwt";
    public static final String JWK = "jwk";
    public static final String JWT_CLOCK_SKEW_IN_SECONDS = "clockSkewInSeconds";
    public static final String ENABLE_VERIFY_JWT = "enableVerifyJwt";
    private static final String ENABLE_JWT_CACHE = "enableJwtCache";
    private static final String ENABLE_RELAXED_KEY_VALIDATION = "enableRelaxedKeyValidation";
    private static final int CACHE_EXPIRED_IN_MINUTES = 15;

    SecurityConfig config;
    int secondsOfAllowedClockSkew;
    static Map<String, List<JsonWebKey>> jwksMap;
    CacheManager cacheManager = CacheManager.getInstance();
    static String audience;  // this is the audience from the client.yml with single oauth provider.
    static Map<String, String> audienceMap; // this is the audience map from the client.yml with multiple oauth providers.


    public JwtVerifier(SecurityConfig config) {
        this.config = config;
        this.secondsOfAllowedClockSkew = config.getClockSkewInSeconds();

        if(Boolean.TRUE.equals(config.isEnableJwtCache())) {
            cache = new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(final Map.Entry eldest) {
                    return size() > 1000;
                }
            };
            logger.debug("jwt cache is enabled.");
        }

    }

    public void initJwkMap() {
        jwksMap = new HashMap<>();
        jwksMap = getJsonWebKeyMap();
    }

    /**
     * This method is to keep backward compatible for those call without VerificationKeyResolver. The single
     * auth server is used in this case.
     *
     * @param jwt          JWT token
     * @param ignoreExpiry indicate if the expiry will be ignored
     * @param pathPrefix   pathPrefix used to cache the jwt token
     * @param requestPath  request path
     * @param jwkServiceIds A list of serviceIds from the UnifiedSecurityHandler
     * @return JwtClaims
     * @throws InvalidJwtException   throw when the token is invalid
     * @throws com.networknt.exception.ExpiredTokenException throw when the token is expired
     */
    public JwtClaims verifyJwt(String jwt, boolean ignoreExpiry, String pathPrefix, String requestPath, List<String> jwkServiceIds) throws InvalidJwtException, ExpiredTokenException {
        return verifyJwt(jwt, ignoreExpiry, pathPrefix, requestPath, jwkServiceIds, this::getKeyResolver);
    }

    /**
     * This method is to keep backward compatible for those call without VerificationKeyResolver.
     * @param jwt JWT token
     * @param ignoreExpiry indicate if the expiry will be ignored
     * @return JwtClaims
     * @throws InvalidJwtException throw when the token is invalid
     */
    public JwtClaims verifyJwt(String jwt, boolean ignoreExpiry) throws InvalidJwtException, ExpiredTokenException {
        return verifyJwt(jwt, ignoreExpiry, null, null, null, this::getKeyResolver);
    }

    /**
     * Verify the jwt token and return the JwtClaims.
     *
     * @param jwt JWT token
     * @param ignoreExpiry indicate if the expiry will be ignored
     * @return JwtClaims
     * @throws InvalidJwtException throw when the token is invalid
     */
    public JwtClaims verifyJwt(String jwt, boolean ignoreExpiry, String pathPrefix, String requestPath, List<String> jwkServiceIds, BiFunction<String, Boolean, VerificationKeyResolver> getKeyResolver) throws InvalidJwtException, ExpiredTokenException {
        JwtClaims claims;
        if (Boolean.TRUE.equals(config.isEnableJwtCache())) {

            if(pathPrefix != null) {
                claims = cache.get(pathPrefix + ":" + jwt);
            } else {
                claims = cache.get(jwt);
            }

            if (claims != null) {
                logger.debug("Got claims from local cache...");
                checkExpiry(ignoreExpiry, claims, secondsOfAllowedClockSkew, null);
                // this claims object is signature verified already
                return claims;
            }
        }

        JwtConsumer consumer;
        JwtConsumerBuilder pKeyBuilder = new JwtConsumerBuilder()
                .setSkipAllValidators()
                .setDisableRequireSignature()
                .setSkipSignatureVerification();

        if (config.isEnableRelaxedKeyValidation()) {
            pKeyBuilder.setRelaxVerificationKeyValidation();
        }

        consumer = pKeyBuilder.build();

        JwtContext jwtContext = consumer.process(jwt);
        claims = jwtContext.getJwtClaims();
        JsonWebStructure structure = jwtContext.getJoseObjects().get(0);
        // need this kid to load public key certificate for signature verification
        String kid = structure.getKeyIdHeaderValue();

        // so we do expiration check here manually as we have the claim already for kid
        // if ignoreExpiry is false, verify expiration of the token
        checkExpiry(ignoreExpiry, claims, secondsOfAllowedClockSkew, jwtContext);

        // validate the audience against the configured audience.
        // validateAudience(claims, requestPath, jwkServiceIds, jwtContext);

        JwtConsumerBuilder jwtBuilder = new JwtConsumerBuilder()
                .setRequireExpirationTime()
                .setAllowedClockSkewInSeconds(315360000) // use seconds of 10 years to skip expiration validation as we need skip it in some cases.
                .setSkipDefaultAudienceValidation()
                .setVerificationKeyResolver(getKeyResolver.apply(kid, true));

        if (config.isEnableRelaxedKeyValidation()) {
            jwtBuilder.setRelaxVerificationKeyValidation();
        }

        consumer = jwtBuilder.build();

        // Validate the JWT and process it to the Claims
        jwtContext = consumer.process(jwt);
        claims = jwtContext.getJwtClaims();
        if (Boolean.TRUE.equals(config.isEnableJwtCache())) {
            if(pathPrefix != null) {
                cache.put(pathPrefix + ":" + jwt, claims);
            } else {
                cache.put(jwt, claims);
            }

            if(cache.size() > config.getJwtCacheFullSize()) {
                logger.warn("JWT cache exceeds the size limit " + config.getJwtCacheFullSize());
            }
        }
        return claims;
    }

    /**
     * Retrieve JWK set from all possible oauth servers. If there are multiple servers in the client.yml, get all
     * the jwk by iterate all of them. In case we have multiple jwks, the cache will have a prefix so that verify
     * action won't cross fired.
     *
     * @return {@link Map} of {@link List}
     */
    @SuppressWarnings("unchecked")
    private Map<String, List<JsonWebKey>> getJsonWebKeyMap() {
        // the jwk indicator will ensure that the kid is not concat to the uri for path parameter.
        // the kid is not needed to get JWK. We need to figure out only one jwk server or multiple.
        ClientConfig clientConfig = ClientConfig.get();
        Map<String, Object> tokenConfig = clientConfig.getTokenConfig();
        Map<String, Object> keyConfig = (Map<String, Object>) tokenConfig.get(ClientConfig.KEY);
        if (clientConfig.isMultipleAuthServers()) {
            // iterate all the configured auth server to get JWK.
            Map<String, Object> serviceIdAuthServers = ClientConfig.getServiceIdAuthServers(keyConfig.get(ClientConfig.SERVICE_ID_AUTH_SERVERS));
            if (serviceIdAuthServers != null && serviceIdAuthServers.size() > 0) {
                audienceMap = new HashMap<>();
                for (Map.Entry<String, Object> entry : serviceIdAuthServers.entrySet()) {
                    String serviceId = entry.getKey();
                    Map<String, Object> authServerConfig = (Map<String, Object>) entry.getValue();
                    // based on the configuration, we can identify if the entry is for jwk retrieval for jwt or swt introspection. For jwk,
                    // there is no clientId and clientSecret. For token introspection, clientId and clientSecret is in the config.
                    if(authServerConfig.get(ClientConfig.CLIENT_ID) != null && authServerConfig.get(ClientConfig.CLIENT_SECRET) != null) {
                        // this is the entry for swt introspection, skip here.
                        continue;
                    }
                    // construct audience map for audience validation.
                    String audience = (String) authServerConfig.get(ClientConfig.AUDIENCE);
                    if (audience != null) {
                        if (logger.isTraceEnabled()) logger.trace("audience {} is mapped to serviceId {}", audience, serviceId);
                        audienceMap.put(serviceId, audience);
                    }
                    // get the jwk from the auth server.
                    TokenKeyRequest keyRequest = new TokenKeyRequest(null, true, authServerConfig);
                    try {
                        if (logger.isDebugEnabled())
                            logger.debug("Getting Json Web Key list from {} for serviceId {}", keyRequest.getServerUrl(), entry.getKey());
                        String key = OauthHelper.getKey(keyRequest);
                        if (logger.isDebugEnabled())
                            logger.debug("Got Json Web Key = " + key);
                        if(key != null) {
                            cacheJwk(key, serviceId);
                        }
                    } catch (ClientException ce) {
                        if (logger.isErrorEnabled())
                            logger.error("Failed to get key. - {} - {} ", new Status(GET_KEY_ERROR), ce.getMessage(), ce);
                    }
                }
            } else {
                // log an error as there is no service entry for the jwk retrieval.
                logger.error("serviceIdAuthServers property is missing or empty in the token key configuration");
            }
        } else {

            // get audience from the key config
            audience = (String) keyConfig.get(ClientConfig.AUDIENCE);
            if(logger.isTraceEnabled()) logger.trace("A single audience {} is configured in client.yml", audience);
            // there is only one jwk server.
            TokenKeyRequest keyRequest = new TokenKeyRequest(null, true, null);
            try {
                if (logger.isDebugEnabled())
                    logger.debug("Getting Json Web Key list from {}", keyRequest.getServerUrl());

                String key = OauthHelper.getKey(keyRequest);
                if (logger.isDebugEnabled())
                    logger.debug("Got Json Web Key = " + key);
                if(key != null) {
                    cacheJwk(key, null);
                }
            } catch (ClientException ce) {
                if (logger.isErrorEnabled())
                    logger.error("Failed to get Key. - {} - {}", new Status(GET_KEY_ERROR), ce.getMessage(), ce);
            }
        }
        return jwksMap;
    }

    /**
     * Retrieve JWK set from an oauth server with the kid. This method is used when a new kid is received
     * and the corresponding jwk doesn't exist in the cache. It will look up the key service by kid first.
     *
     * @param kid         String of kid
     * @param requestPathOrJwkServiceIds String of request path or list of strings for jwkServiceIds
     * @return {@link List} of {@link JsonWebKey}
     */
    @SuppressWarnings("unchecked")
    private String getJsonWebKeySetForToken(String kid, Object requestPathOrJwkServiceIds) {
        // the jwk indicator will ensure that the kid is not concat to the uri for path parameter.
        // the kid is not needed to get JWK, but if requestPath is not null, it will be used to get the keyConfig
        if (logger.isTraceEnabled()) {
            logger.trace("kid = " + kid + " requestPathOrJwkServiceIds = " + requestPathOrJwkServiceIds);
            if(requestPathOrJwkServiceIds instanceof List) {
                ((List<String>)requestPathOrJwkServiceIds).forEach(logger::trace);
            }
        }
        ClientConfig clientConfig = ClientConfig.get();
        String key = null;
        Map<String, Object> config = null;

        if (requestPathOrJwkServiceIds != null && clientConfig.isMultipleAuthServers()) {
            if(requestPathOrJwkServiceIds instanceof String) {
                String requestPath = (String)requestPathOrJwkServiceIds;
                Map<String, String> pathPrefixServices = clientConfig.getPathPrefixServices();
                if (pathPrefixServices == null || pathPrefixServices.size() == 0) {
                    throw new ConfigException("pathPrefixServices property is missing or has an empty value in client.yml");
                }
                // lookup the serviceId based on the full path and the prefix mapping by iteration here.
                String serviceId = null;
                for (Map.Entry<String, String> entry : pathPrefixServices.entrySet()) {
                    if (requestPath.startsWith(entry.getKey())) {
                        serviceId = entry.getValue();
                    }
                }
                if (serviceId == null) {
                    throw new ConfigException("serviceId cannot be identified in client.yml with the requestPath = " + requestPath);
                }
                config = getJwkConfig(clientConfig, serviceId);
                key = retrieveJwk(kid, config);
            } else if (requestPathOrJwkServiceIds instanceof List) {
                List<String> jwkServiceIds = (List<String>)requestPathOrJwkServiceIds;
                for(String serviceId: jwkServiceIds) {
                    config = getJwkConfig(clientConfig, serviceId);
                    key = retrieveJwk(kid, config);
                    if(key != null && key.contains(kid)) // right jwk for the kid is found, break the loop.
                        break;
                }
            } else {
                throw new ConfigException("requestPathOrJwkServiceIds must be a string or a list of strings");
            }
        } else {
            // get the jwk from the key section in the client.yml token key.
            key = retrieveJwk(kid, null);
        }
        return key;
    }

    private String retrieveJwk(String kid, Map<String, Object> config) {
        // get the jwk with the kid and config map.
        if (logger.isTraceEnabled() && config != null)
            logger.trace("multiple oauth config based on path = " + JsonMapper.toJson(config));
        // config is not null if isMultipleAuthServers is true. If it is null, then the key section is used from the client.yml
        TokenKeyRequest keyRequest = new TokenKeyRequest(kid, true, config);

        try {
            if (logger.isDebugEnabled())
                logger.debug("Getting Json Web Key list from {}", keyRequest.getServerUrl());

            String key = OauthHelper.getKey(keyRequest);

            if (logger.isDebugEnabled())
                logger.debug("Got Json Web Key {} from {} with path {}", key, keyRequest.getServerUrl(), keyRequest.getUri());

            return key;
        } catch (ClientException ce) {
            if (logger.isErrorEnabled())
                logger.error("Failed to get key - {} - {}", new Status(GET_KEY_ERROR), ce.getMessage(), ce);
        }
        return null;
    }

    /**
     * Checks expiry of a jwt token from the claim.
     *
     * @param ignoreExpiry     - flag set if we want to ignore expired tokens or not.
     * @param claim            - jwt claims
     * @param allowedClockSkew - seconds of allowed skew in token expiry
     * @param context          - jwt context
     * @throws com.networknt.exception.ExpiredTokenException - thrown when token is expired
     * @throws InvalidJwtException   - thrown when the token is malformed/invalid
     */
    private static void checkExpiry(boolean ignoreExpiry, JwtClaims claim, int allowedClockSkew, JwtContext context) throws com.networknt.exception.ExpiredTokenException, InvalidJwtException {
        if (!ignoreExpiry) {
            try {
                // if using our own client module, the jwt token should be renewed automatically
                // and it will never expire here. However, we need to handle other clients.
                if ((NumericDate.now().getValue() - allowedClockSkew) >= claim.getExpirationTime().getValue()) {
                    logger.info("Cached jwt token is expired!");
                    throw new com.networknt.exception.ExpiredTokenException("Token is expired");
                }
            } catch (MalformedClaimException e) {
                // This is cached token and it is impossible to have this exception
                logger.error("MalformedClaimException:", e);
                throw new InvalidJwtException("MalformedClaimException", new ErrorCodeValidator.Error(ErrorCodes.MALFORMED_CLAIM, "Invalid ExpirationTime Format"), e, context);
            }
        }
    }

    /**
     * Get VerificationKeyResolver based on the kid and isToken indicator. For the implementation, we check
     * the jwk first and 509Certificate if the jwk cannot find the kid. Basically, we want to iterate all
     * the resolvers and find the right one with the kid.
     *
     * @param kid key id from the JWT token
     * @return VerificationKeyResolver
     */
    private VerificationKeyResolver getKeyResolver(String kid, Object requestPathOrJwkServiceIds) {
        // jwk is always used here. get from jwksMap first.
        ClientConfig clientConfig = ClientConfig.get();
        List<JsonWebKey> jwkList = null;
        if(requestPathOrJwkServiceIds == null) {
            // single oauth server, kid is the key for the jwk cache. get the jwkList from the jwksMap first and then cacheManager.
            jwkList = getCachedJwk(kid, null);

        } else if(requestPathOrJwkServiceIds instanceof String) {
            String requestPath = (String)requestPathOrJwkServiceIds;
            // a single request path is passed in.
            String serviceId = getServiceIdByRequestPath(clientConfig, requestPath);
            if(serviceId == null) {
                jwkList = getCachedJwk(kid, null);
            } else {
                jwkList = getCachedJwk(kid, serviceId);
            }
        } else if(requestPathOrJwkServiceIds instanceof List) {
            List<String> serviceIds = (List)requestPathOrJwkServiceIds;
            if(serviceIds != null && serviceIds.size() > 0) {
                // more than one serviceIds are passed in from the UnifiedSecurityHandler. Just use the serviceId and kid
                // combination to look up the jwkList. Once found, break the loop.
                for(String serviceId: serviceIds) {
                    jwkList = getCachedJwk(kid, serviceId);
                    if(jwkList != null && jwkList.size() > 0) {
                        break;
                    }
                }
            }
        }

        if (jwkList == null) {
            String key = getJsonWebKeySetForToken(kid, requestPathOrJwkServiceIds);
            if (key == null) {
                throw new RuntimeException("no JWK for kid: " + kid);
            }

            try {
                jwkList = new JsonWebKeySet(key).getJsonWebKeys();
            } catch (JoseException e) {
                throw new RuntimeException(e);
            }

            if(requestPathOrJwkServiceIds == null) {
                // single jwk setup and kid is the key for the jwk cache.
                cacheJwk(key, null);
            } else if(requestPathOrJwkServiceIds instanceof String) {
                // a single request path is passed in.
                String serviceId = getServiceIdByRequestPath(clientConfig, (String)requestPathOrJwkServiceIds);
                cacheJwk(key, serviceId);
            } else if(requestPathOrJwkServiceIds instanceof List) {
                // called with a list of serviceIds from the UnifiedSecurityHandler.
                for(String serviceId: (List<String>)requestPathOrJwkServiceIds) {
                    cacheJwk(key, serviceId);
                }
            }
        }

        logger.debug("Got Json web key set from local cache");
        return new JwksVerificationKeyResolver(jwkList);
    }

    private String getServiceIdByRequestPath(ClientConfig clientConfig, String requestPath) {
        Map<String, String> pathPrefixServices = clientConfig.getPathPrefixServices();
        if(clientConfig.isMultipleAuthServers()) {
            if (pathPrefixServices == null || pathPrefixServices.size() == 0) {
                throw new ConfigException("pathPrefixServices property is missing or has an empty value in client.yml");
            }
            // lookup the serviceId based on the full path and the prefix mapping by iteration here.
            String serviceId = null;
            for (Map.Entry<String, String> entry : pathPrefixServices.entrySet()) {
                if (requestPath.startsWith(entry.getKey())) {
                    serviceId = entry.getValue();
                }
            }
            if (serviceId == null) {
                throw new ConfigException("serviceId cannot be identified in client.yml with the requestPath = " + requestPath);
            }
            return serviceId;
        } else {
            return null;
        }
    }

    private List<JsonWebKey> getCachedJwk(String kid, String serviceId) {
        List<JsonWebKey> jwkList = null;
        if(serviceId != null) {
            jwkList = jwksMap.get(serviceId + ":" + kid);
            if(jwkList == null) {
                String key = (String)cacheManager.get(LambdaApp.CONFIG.getLambdaAppId() + ":" + JWK, serviceId + ":" + kid);
                logger.debug("JWK From Cache: {}", key);
                if(key != null) {
                    try {
                        jwkList = new JsonWebKeySet(key).getJsonWebKeys();
                    } catch (JoseException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } else {
            jwkList = jwksMap.get(kid);
            if(jwkList == null) {
                String key = (String)cacheManager.get(LambdaApp.CONFIG.getLambdaAppId() + ":" + JWK, kid);
                logger.debug("JWK From Cache: {}", key);
                if(key != null) {
                    try {
                        jwkList = new JsonWebKeySet(key).getJsonWebKeys();
                    } catch (JoseException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        return jwkList;
    }

    private void cacheJwk(String key, String serviceId) {
        // key shouldn't be null at this moment.
        List<JsonWebKey> jwkList;
        try {
            jwkList = new JsonWebKeySet(key).getJsonWebKeys();
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
        for (JsonWebKey jwk : jwkList) {
            if(serviceId != null) {
                if(logger.isTraceEnabled()) logger.trace("cache the jwkList with serviceId {} kid {} and key {}", serviceId, jwk.getKeyId(), serviceId + ":" + jwk.getKeyId());
                jwksMap.put(serviceId + ":" + jwk.getKeyId(), jwkList);
                if (logger.isDebugEnabled())
                    logger.debug("Successfully cached JWK in jwksMap for serviceId {} kid {} with key {}", serviceId, jwk.getKeyId(), serviceId + ":" + jwk.getKeyId());
                // cacheName is the serviceId + ":" + jwk or jwt which is the table name.
                if(cacheManager != null) cacheManager.put(LambdaApp.CONFIG.getLambdaAppId() + ":" + JWK, serviceId + ":" + jwk.getKeyId(), key);
                if(logger.isDebugEnabled())
                    logger.debug("Successfully cached JWK in cacheManager for serviceId {} kid {} with key {}", LambdaApp.CONFIG.getLambdaAppId(), jwk.getKeyId(), serviceId + ":" + jwk.getKeyId());
            } else {
                if(logger.isTraceEnabled()) logger.trace("cache the jwkList with kid and only kid as key", jwk.getKeyId());

                jwksMap.put(jwk.getKeyId(), jwkList);
                if (logger.isDebugEnabled())
                    logger.debug("Successfully cached JWK in jwksMap for kid {} with key {}", jwk.getKeyId(), jwk.getKeyId());

                if(cacheManager != null) cacheManager.put(LambdaApp.CONFIG.getLambdaAppId() + ":" + JWK, jwk.getKeyId(), key);
                if(logger.isDebugEnabled())
                    logger.debug("Successfully cached JWK in cacheManager for serviceId {} kid {} with key {}", LambdaApp.CONFIG.getLambdaAppId(), jwk.getKeyId(), jwk.getKeyId());

            }
        }
    }

}
