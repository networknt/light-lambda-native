package com.networknt.aws.lambda.handler.middleware.security;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.header.HeaderMiddleware;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.config.Config;
import com.networknt.exception.ExpiredTokenException;
import com.networknt.header.HeaderConfig;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.SecurityParameter;
import com.networknt.oas.model.SecurityRequirement;
import com.networknt.openapi.OpenApiOperation;
import com.networknt.security.SecurityConfig;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;
import static com.networknt.aws.lambda.utility.HeaderKey.SCOPE_TOKEN;

public class JwtVerifyMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(JwtVerifyMiddleware.class);
    private static final SecurityConfig config = SecurityConfig.load(SecurityConfig.CONFIG_NAME);
    public static JwtVerifier jwtVerifier;

    static final String STATUS_INVALID_AUTH_TOKEN = "ERR10000";
    static final String STATUS_AUTH_TOKEN_EXPIRED = "ERR10001";
    static final String STATUS_MISSING_AUTH_TOKEN = "ERR10002";
    static final String STATUS_INVALID_SCOPE_TOKEN = "ERR10003";
    static final String STATUS_SCOPE_TOKEN_EXPIRED = "ERR10004";
    static final String STATUS_AUTH_TOKEN_SCOPE_MISMATCH = "ERR10005";
    static final String STATUS_SCOPE_TOKEN_SCOPE_MISMATCH = "ERR10006";
    static final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    static final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";
    static final String STATUS_OPENAPI_OPERATION_MISSED = "ERR10085";
    public JwtVerifyMiddleware() {
        if(LOG.isInfoEnabled()) LOG.info("JwtVerifyMiddleware is constructed");
        jwtVerifier = new JwtVerifier(config);
        jwtVerifier.initJwkMap();
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
        if(LOG.isDebugEnabled()) LOG.debug("JwtVerifyMiddleware.executeMiddleware starts");

        LOG.debug("JWT Verification Time - Start: {}", System.currentTimeMillis());

        String reqPath = exchange.getRequest().getPath();
        if (config.getSkipPathPrefixes() != null && config.getSkipPathPrefixes().stream().anyMatch(reqPath::startsWith)) {
            if(LOG.isTraceEnabled())
                LOG.trace("Skip request path base on skipPathPrefixes for " + reqPath);
            return successMiddlewareStatus();
        }
        // only UnifiedSecurityHandler will have the jwkServiceIds as the third parameter.
        return handleJwt(exchange, null, reqPath, null);
    }

    public Status handleJwt(LightLambdaExchange exchange, String pathPrefix, String reqPath, List<String> jwkServiceIds) {
        Map<String, Object> auditInfo = null;
        Map<String, String> headerMap = exchange.getRequest().getHeaders();
        String authorization = (headerMap.get(HeaderKey.AUTHORIZATION_UPPER) == null) ? headerMap.get(HeaderKey.AUTHORIZATION_LOWER) : headerMap.get(HeaderKey.AUTHORIZATION_UPPER);

        if (LOG.isTraceEnabled()) LOG.trace("pathPrefix = {} and reqPath = {} and headerMap = {}", pathPrefix, reqPath, headerMap.isEmpty() ? "empty" : headerMap.toString());

        if (LOG.isTraceEnabled() && authorization != null && authorization.length() > 10)
            LOG.trace("Authorization header = " + authorization.substring(0, 10));
        // if an empty authorization header or a value length less than 6 ("Basic "), return an error
        if(authorization == null ) {
            if (LOG.isDebugEnabled()) LOG.debug("JwtVerifyMiddleware.executeMiddleware ends with an error. Authorization header value is NULL");
            return new Status(STATUS_MISSING_AUTH_TOKEN);
        } else if(authorization.trim().length() < 6) {
            if (LOG.isDebugEnabled()) LOG.debug("JwtVerifyMiddleware.executeMiddleware ends with an error.");
            return new Status(STATUS_INVALID_AUTH_TOKEN);
        } else {
            authorization = this.getScopeToken(authorization, headerMap);

            boolean ignoreExpiry = config.isIgnoreJwtExpiry();

            String jwt = JwtVerifier.getTokenFromAuthorization(authorization);

            if (jwt != null) {
                if (LOG.isTraceEnabled())
                    LOG.trace("parsed jwt from authorization = " + jwt.substring(0, 10));
                try {
                    JwtClaims claims = jwtVerifier.verifyJwt(jwt, ignoreExpiry, pathPrefix, reqPath, jwkServiceIds);
                    if (LOG.isTraceEnabled())
                        LOG.trace("claims = " + claims.toJson());

                    String clientId = claims.getStringClaimValue(Constants.CLIENT_ID_STRING);
                    String userId = claims.getStringClaimValue(Constants.USER_ID_STRING);
                    String issuer = claims.getStringClaimValue(Constants.ISS_STRING);
                    // try to get the cid as some OAuth tokens name it as cid like Okta.
                    if (clientId == null)
                        clientId = claims.getStringClaimValue(Constants.CID_STRING);


                    // try to get the uid as some OAuth tokens name it as uid like Okta.
                    if (userId == null)
                        userId = claims.getStringClaimValue(Constants.UID_STRING);

                    /* if no auditInfo has been set previously, we populate here */
                    auditInfo = (exchange.getRequestAttachment(AUDIT_ATTACHMENT_KEY) != null) ? (Map<String, Object>) exchange.getRequestAttachment(AUDIT_ATTACHMENT_KEY) : new HashMap<>();
                    auditInfo.put(Constants.USER_ID_STRING, userId);
                    auditInfo.put(Constants.SUBJECT_CLAIMS, claims);
                    auditInfo.put(Constants.CLIENT_ID_STRING, clientId);
                    auditInfo.put(Constants.ISSUER_CLAIMS, issuer);
                    Map<String, String> headers = exchange.getRequest().getHeaders();
                    String callerId = headers.get(Constants.CALLER_ID_STRING);

                    if (callerId != null)
                        auditInfo.put(Constants.CALLER_ID_STRING, callerId);

                    exchange.addRequestAttachment(AUDIT_ATTACHMENT_KEY, auditInfo);

                    if (config.isEnableVerifyScope()) {
                        if (LOG.isTraceEnabled())
                            LOG.trace("verify scope from the primary token when enableVerifyScope is true");

                        /* get openapi operation */
                        OpenApiOperation openApiOperation = (OpenApiOperation) auditInfo.get(Constants.OPENAPI_OPERATION_STRING);

                        // here we assume that the OpenApiMiddleware has been executed before this middleware and the openApiOperation is set in the auditInfo.
                        Operation operation = openApiOperation.getOperation();

                        if(operation == null) {

                            if(config.isSkipVerifyScopeWithoutSpec()) {

                                if (LOG.isDebugEnabled())
                                    LOG.debug("JwtVerifyHandler.handleRequest ends without verifying scope due to spec.");

                                return successMiddlewareStatus();
                            } else {
                                // this will return an error message to the client.
                            }

                            if (LOG.isDebugEnabled())
                                LOG.debug("JwtVerifyHandler.handleRequest ends with an error.");

                            return new Status(STATUS_OPENAPI_OPERATION_MISSED);
                        }

                        /* validate scope from operation */
                        String scopeHeader = headers.get(SCOPE_TOKEN);
                        String scopeJwt = JwtVerifier.getTokenFromAuthorization(scopeHeader);
                        List<String> secondaryScopes = new ArrayList<>();
                        Status status = this.hasValidSecondaryScopes(scopeJwt, secondaryScopes, ignoreExpiry, pathPrefix, reqPath, jwkServiceIds, auditInfo);
                        if(status != null) {
                            if (LOG.isDebugEnabled()) LOG.debug("JwtVerifyHandler.handleRequest ends with an error.");
                            return status;
                        }
                        status = this.hasValidScope(scopeHeader, secondaryScopes, claims, operation);
                        if(status != null) {
                            if (LOG.isDebugEnabled()) LOG.debug("JwtVerifyHandler.handleRequest ends with an error.");
                            return status;
                        }
                    }

                    // pass through claims through request headers after verification is done.
                    if(config.getPassThroughClaims() != null && config.getPassThroughClaims().size() > 0) {
                        for(Map.Entry<String, String> entry: config.getPassThroughClaims().entrySet()) {
                            String key = entry.getKey();
                            String header = entry.getValue();
                            Object value = claims.getClaimValue(key);
                            if(LOG.isTraceEnabled()) LOG.trace("pass through header {} with value {}", header, value);
                            headers.put(header, value.toString());
                        }
                    }

                    if (LOG.isTraceEnabled())
                        LOG.trace("complete JWT verification for request path = " + exchange.getRequest().getPath());

                    LOG.debug("JWT Verification Time - Finish: {}", System.currentTimeMillis());
                    if (LOG.isDebugEnabled())
                        LOG.debug("JwtVerifyHandler.handleRequest ends.");

                    return successMiddlewareStatus();

                } catch (InvalidJwtException e) {

                    // only log it and unauthorized is returned.
                    LOG.error("InvalidJwtException: ", e);

                    if (LOG.isDebugEnabled())
                        LOG.debug("JwtVerifyHandler.handleRequest ends with an error.");

                    return new Status(STATUS_INVALID_AUTH_TOKEN);

                } catch (ExpiredTokenException e) {
                    LOG.error("ExpiredTokenException", e);

                    if (LOG.isDebugEnabled())
                        LOG.debug("JwtVerifyHandler.handleRequest ends with an error.");

                    return new Status(STATUS_AUTH_TOKEN_EXPIRED);

                } catch (MalformedClaimException e) {
                    LOG.error("MalformedClaimException", e);
                    if (LOG.isDebugEnabled())
                        LOG.debug("JwtVerifyHandler.handleRequest ends with an error.");
                    return new Status(STATUS_INVALID_AUTH_TOKEN);
                }
            } else {
                if (LOG.isDebugEnabled())
                    LOG.debug("JwtVerifyHandler.handleRequest ends with an error. Cannot extract Bearer Token from Authorization header");
                return new Status(STATUS_MISSING_AUTH_TOKEN);
            }
        }
    }

    /**
     * Get authToken from X-Scope-Token header.
     * This covers situations where there is a secondary auth token.
     *
     * @param authorization - The auth token from authorization header
     * @param headerMap - complete header map
     * @return - return either x-scope-token or the initial auth token
     */
    protected String getScopeToken(String authorization, Map<String, String> headerMap) {
        String returnToken = authorization;
        // in the gateway case, the authorization header might be a basic header for the native API or other authentication headers.
        // this will allow the Basic authentication be wrapped up with a JWT token between proxy client and proxy server for native.
        if (returnToken != null && !returnToken.substring(0, 6).equalsIgnoreCase("Bearer")) {

            // get the jwt token from the X-Scope-Token header in this case and allow the verification done with the secondary token.
            returnToken = headerMap.get(SCOPE_TOKEN);

            if (LOG.isTraceEnabled() && returnToken != null && returnToken.length() > 10)
                LOG.trace("The replaced authorization from X-Scope-Token header = " + returnToken.substring(0, 10));
        }
        return returnToken;
    }

    /**
     * Check is the request has secondary scopes and they are valid.
     *
     * @param scopeJwt - the scope found in jwt
     * @param secondaryScopes - Initially an empty list that is then filled with the secondary scopes if there are any.
     * @param ignoreExpiry - if we ignore expiry or not (mostly for testing)
     * @param pathPrefix - request path prefix
     * @param reqPath - the request path as string
     * @param jwkServiceIds - a list of serviceIds for jwk loading
     * @param auditInfo - a map of audit info properties
     * @return - return null if there is no error. Otherwise, return the error status.
     */
    protected Status hasValidSecondaryScopes(String scopeJwt, List<String> secondaryScopes, boolean ignoreExpiry, String pathPrefix, String reqPath, List<String> jwkServiceIds, Map<String, Object> auditInfo) {
        if (scopeJwt != null) {
            if (LOG.isTraceEnabled())
                LOG.trace("start verifying scope token = " + scopeJwt.substring(0, 10));

            try {
                JwtClaims scopeClaims = jwtVerifier.verifyJwt(scopeJwt, ignoreExpiry, pathPrefix, reqPath, jwkServiceIds);
                Object scopeClaim = scopeClaims.getClaimValue(Constants.SCOPE_STRING);

                if (scopeClaim instanceof String) {
                    secondaryScopes.addAll(Arrays.asList(scopeClaims.getStringClaimValue(Constants.SCOPE_STRING).split(" ")));
                } else if (scopeClaim instanceof List) {
                    secondaryScopes.addAll(scopeClaims.getStringListClaimValue(Constants.SCOPE_STRING));
                }

                if (secondaryScopes.isEmpty()) {

                    // some IDPs like Okta and Microsoft call scope claim "scp" instead of "scope"
                    Object scpClaim = scopeClaims.getClaimValue(Constants.SCP_STRING);
                    if (scpClaim instanceof String) {
                        secondaryScopes.addAll(Arrays.asList(scopeClaims.getStringClaimValue(Constants.SCP_STRING).split(" ")));
                    } else if (scpClaim instanceof List) {
                        secondaryScopes.addAll(scopeClaims.getStringListClaimValue(Constants.SCP_STRING));
                    }
                }
                auditInfo.put(Constants.SCOPE_CLIENT_ID_STRING, scopeClaims.getStringClaimValue(Constants.CLIENT_ID_STRING));
                auditInfo.put(Constants.ACCESS_CLAIMS, scopeClaims);
            } catch (InvalidJwtException e) {
                LOG.error("InvalidJwtException", e);
                return new Status(STATUS_INVALID_SCOPE_TOKEN);
            } catch (MalformedClaimException e) {
                LOG.error("MalformedClaimException", e);
                return new Status(STATUS_INVALID_AUTH_TOKEN);
            } catch (ExpiredTokenException e) {
                LOG.error("ExpiredTokenException", e);
                return new Status(STATUS_SCOPE_TOKEN_EXPIRED);
            }
        }
        return null;
    }

    /**
     * Makes sure the provided scope in the JWT is valid for the main scope or secondary scopes.
     *
     * @param scopeHeader - the scope header
     * @param secondaryScopes - list of secondary scopes (can be empty)
     * @param claims - claims found in jwt
     * @param operation - the openapi operation
     * @return - return a Status object if there are any error. Otherwise, return null.
     */
    protected Status hasValidScope(String scopeHeader, List<String> secondaryScopes, JwtClaims claims, Operation operation) {

        // validate the scope against the scopes configured in the OpenAPI spec
        if (config.isEnableVerifyScope()) {
            // get scope defined in OpenAPI spec for this endpoint.
            Collection<String> specScopes = null;
            Collection<SecurityRequirement> securityRequirements = operation.getSecurityRequirements();
            if (securityRequirements != null) {
                for (SecurityRequirement requirement : securityRequirements) {
                    SecurityParameter securityParameter = null;

                    for (String oauth2Name : OpenApiMiddleware.helper.oauth2Names) {
                        securityParameter = requirement.getRequirement(oauth2Name);
                        if (securityParameter != null) break;
                    }

                    if (securityParameter != null)
                        specScopes = securityParameter.getParameters();

                    if (specScopes != null)
                        break;
                }
            }

            // validate scope
            if (scopeHeader != null) {
                if (LOG.isTraceEnabled()) LOG.trace("validate the scope with scope token");
                if (secondaryScopes == null || !matchedScopes(secondaryScopes, specScopes)) {
                    return new Status(STATUS_SCOPE_TOKEN_SCOPE_MISMATCH, secondaryScopes, specScopes);
                }
            } else {
                // no scope token, verify scope from auth token.
                if (LOG.isTraceEnabled()) LOG.trace("validate the scope with primary token");
                List<String> primaryScopes = null;
                try {
                    Object scopeClaim = claims.getClaimValue(Constants.SCOPE_STRING);
                    if (scopeClaim instanceof String) {
                        primaryScopes = Arrays.asList(claims.getStringClaimValue(Constants.SCOPE_STRING).split(" "));
                    } else if (scopeClaim instanceof List) {
                        primaryScopes = claims.getStringListClaimValue(Constants.SCOPE_STRING);
                    }
                    if (primaryScopes == null || primaryScopes.isEmpty()) {
                        // some IDPs like Okta and Microsoft call scope claim "scp" instead of "scope"
                        Object scpClaim = claims.getClaimValue(Constants.SCP_STRING);
                        if (scpClaim instanceof String) {
                            primaryScopes = Arrays.asList(claims.getStringClaimValue(Constants.SCP_STRING).split(" "));
                        } else if (scpClaim instanceof List) {
                            primaryScopes = claims.getStringListClaimValue(Constants.SCP_STRING);
                        }
                    }
                } catch (MalformedClaimException e) {
                    LOG.error("MalformedClaimException", e);
                    return new Status(STATUS_INVALID_AUTH_TOKEN);
                }
                if (!matchedScopes(primaryScopes, specScopes)) {
                    LOG.error("Authorization token scope is not matched.");
                    return new Status(STATUS_AUTH_TOKEN_SCOPE_MISMATCH, primaryScopes, specScopes);
                }
            }
        }
        return null;
    }

    protected boolean matchedScopes(List<String> jwtScopes, Collection<String> specScopes) {
        boolean matched = false;
        if (specScopes != null && specScopes.size() > 0) {
            if (jwtScopes != null && jwtScopes.size() > 0) {
                for (String scope : specScopes) {
                    if (jwtScopes.contains(scope)) {
                        matched = true;
                        break;
                    }
                }
            }
        } else {
            matched = true;
        }
        return matched;
    }

    @Override
    public void getCachedConfigurations() {
    }

    @Override
    public boolean isEnabled() {
        return config.isEnableVerifyJwt();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                SecurityConfig.CONFIG_NAME,
                JwtVerifyMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(SecurityConfig.CONFIG_NAME),
                null
        );
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
