package com.networknt.aws.lambda.handler.middleware.security;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.MapUtil;
import com.networknt.client.oauth.TokenInfo;
import com.networknt.monad.Result;
import com.networknt.oas.model.Operation;
import com.networknt.oas.model.SecurityParameter;
import com.networknt.oas.model.SecurityRequirement;
import com.networknt.openapi.OpenApiOperation;
import com.networknt.security.SecurityConfig;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;
import static com.networknt.aws.lambda.utility.HeaderKey.SCOPE_TOKEN;

public class SwtVerifyMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SwtVerifyMiddleware.class);

    static final String OPENAPI_SECURITY_CONFIG = "openapi-security";
    static final String STATUS_INVALID_AUTH_TOKEN = "ERR10000";
    static final String STATUS_MISSING_AUTH_TOKEN = "ERR10002";
    static final String STATUS_AUTH_TOKEN_SCOPE_MISMATCH = "ERR10005";
    static final String STATUS_SCOPE_TOKEN_SCOPE_MISMATCH = "ERR10006";
    static final String STATUS_INVALID_REQUEST_PATH = "ERR10007";
    static final String STATUS_METHOD_NOT_ALLOWED = "ERR10008";
    static final String STATUS_CLIENT_EXCEPTION = "ERR10082";
    static final String STATUS_OPENAPI_OPERATION_MISSED = "ERR10085";

    public static SwtVerifier swtVerifier;

    private static SecurityConfig CONFIG;

    public SwtVerifyMiddleware() {
        CONFIG = SecurityConfig.load(OPENAPI_SECURITY_CONFIG);
        swtVerifier = new SwtVerifier(CONFIG);
        if(LOG.isInfoEnabled()) LOG.info("SwtVerifyMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute starts.");

        String reqPath = exchange.getRequest().getPath();
        // if request path is in the skipPathPrefixes in the config, call the next handler directly to skip the security check.
        if (CONFIG.getSkipPathPrefixes() != null && CONFIG.getSkipPathPrefixes().stream().anyMatch(reqPath::startsWith)) {
            if(LOG.isTraceEnabled()) LOG.trace("Skip request path base on skipPathPrefixes for " + reqPath);
            if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute ends.");
            return successMiddlewareStatus();
        }
        // only UnifiedSecurityHandler will have the jwkServiceIds as the third parameter.
        return handleSwt(exchange, reqPath, null);
    }

    public Status handleSwt(LightLambdaExchange exchange, String reqPath, List<String> jwkServiceIds) {
        Map<String, Object> auditInfo = null;
        Map<String, String> headerMap = exchange.getRequest().getHeaders();
        Optional<String> optionalAuth = MapUtil.getValueIgnoreCase(headerMap, HeaderKey.AUTHORIZATION);

        if (LOG.isTraceEnabled()) LOG.trace("reqPath = {} and headerMap = {}", reqPath, headerMap.isEmpty() ? "empty" : headerMap.toString());

        // if an empty authorization header or a value length less than 6 ("Basic "), return an error
        if(optionalAuth.isEmpty()) {
            if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute ends with an error. Authorization header value is NULL.");
            return new Status(STATUS_MISSING_AUTH_TOKEN);
        } else {
            // the authorization header is not empty
            String authorization = optionalAuth.get();
            if(authorization.trim().length() < 6) {
                if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute ends with an error.");
                return new Status(STATUS_INVALID_AUTH_TOKEN);
            } else {
                if (LOG.isTraceEnabled() && authorization.length() > 10)
                    LOG.trace("Authorization header = " + authorization.substring(0, 10));

                authorization = this.getScopeToken(authorization, headerMap);
                String swt = SwtVerifier.getTokenFromAuthorization(authorization);
                if (swt != null) {
                    if (LOG.isTraceEnabled()) LOG.trace("parsed swt from authorization = " + swt.substring(0, 10));
                    Optional<String> optionalSwtClientId = MapUtil.getValueIgnoreCase(headerMap, CONFIG.getSwtClientIdHeader());
                    Optional<String> optionalSwtClientSecret = MapUtil.getValueIgnoreCase(headerMap, CONFIG.getSwtClientSecretHeader());

                    if(LOG.isTraceEnabled()) LOG.trace("header swtClientId = " + optionalSwtClientId.orElse(null) + ", header swtClientSecret = " + StringUtils.maskHalfString(optionalSwtClientSecret.orElse(null)));
                    Result<TokenInfo> tokenInfoResult = swtVerifier.verifySwt(swt, reqPath, jwkServiceIds, optionalSwtClientId.orElse(null), optionalSwtClientSecret.orElse(null));
                    if(tokenInfoResult.isFailure()) {
                        // return error status to the user.
                        if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute ends with an error.");
                        return tokenInfoResult.getError();
                    }
                    TokenInfo tokenInfo = tokenInfoResult.getResult();
                    /* if no auditInfo has been set previously, we populate here */
                    auditInfo = (exchange.getAttachment(AUDIT_ATTACHMENT_KEY) != null) ? (Map<String, Object>) exchange.getAttachment(AUDIT_ATTACHMENT_KEY) : new HashMap<>();
                    String clientId = tokenInfo.getClientId();
                    auditInfo.put(Constants.CLIENT_ID_STRING, clientId);
                    String issuer = tokenInfo.getIss();
                    auditInfo.put(Constants.ISSUER_CLAIMS, issuer);
                    Map<String, String> headers = exchange.getRequest().getHeaders();
                    String callerId = headers.get(Constants.CALLER_ID_STRING);
                    if (callerId != null)
                        auditInfo.put(Constants.CALLER_ID_STRING, callerId);
                    exchange.addAttachment(AUDIT_ATTACHMENT_KEY, auditInfo);

                    if (CONFIG != null && CONFIG.isEnableVerifyScope()) {
                        if (LOG.isTraceEnabled()) LOG.trace("verify scope from the primary token when enableVerifyScope is true");

                        /* get openapi operation */
                        OpenApiOperation openApiOperation = (OpenApiOperation) auditInfo.get(Constants.OPENAPI_OPERATION_STRING);
                        // here we assume that the OpenApiMiddleware has been executed before this middleware and the openApiOperation is set in the auditInfo.
                        Operation operation = openApiOperation.getOperation();
                        if(operation == null) {
                            if(CONFIG.isSkipVerifyScopeWithoutSpec()) {
                                if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute ends without verifying scope due to spec.");
                                return successMiddlewareStatus();
                            } else {
                                // this will return an error message to the client.
                            }
                            if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute ends with an error.");
                            return new Status(STATUS_OPENAPI_OPERATION_MISSED);
                        }

                        /* validate scope from operation */
                        Optional<String> optionalScopeHeader = MapUtil.getValueIgnoreCase(headerMap, SCOPE_TOKEN);
                        String scopeSwt = SwtVerifier.getTokenFromAuthorization(optionalScopeHeader.orElse(null));
                        List<String> secondaryScopes = new ArrayList<>();

                        Status status = hasValidSecondaryScopes(exchange, optionalScopeHeader.orElse(null), secondaryScopes, reqPath, jwkServiceIds, auditInfo);
                        if(status.getStatusCode() >= 400) {
                            if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute ends with an error.");
                            return status;
                        }

                        status = hasValidScope(optionalScopeHeader.orElse(null), secondaryScopes, tokenInfo, operation);
                        if(status.getStatusCode() >= 400) {
                            if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute ends with an error.");
                            return status;
                        }
                    }
                    // pass through claims through request headers after verification is done.
                    if(CONFIG.getPassThroughClaims() != null && !CONFIG.getPassThroughClaims().isEmpty()) {
                        try {
                            for (Map.Entry<String, String> entry : CONFIG.getPassThroughClaims().entrySet()) {
                                String key = entry.getKey();
                                String header = entry.getValue();
                                Field field = tokenInfo.getClass().getDeclaredField(key);
                                field.setAccessible(true);
                                Object value = field.get(tokenInfo);
                                if (LOG.isTraceEnabled())
                                    LOG.trace("pass through header {} with value {}", header, value);
                                headerMap.put(header, value.toString());
                            }
                        } catch (Exception e) {
                            LOG.error("Exception:", e);
                        }
                    }
                    if (LOG.isTraceEnabled())
                        LOG.trace("complete SWT verification for request path = " + exchange.getRequest().getPath());

                    if (LOG.isDebugEnabled())
                        LOG.debug("SwtVerifyMiddleware.execute ends.");

                    return successMiddlewareStatus();
                } else {
                    if (LOG.isDebugEnabled()) LOG.debug("SwtVerifyMiddleware.execute ends with an error.");
                    return new Status(STATUS_MISSING_AUTH_TOKEN);
                }
            }

        }

    }

    /**
     * Makes sure the provided scope in the JWT or SWT is valid for the main scope or secondary scopes.
     *
     * @param scopeHeader - the scope header
     * @param secondaryScopes - list of secondary scopes (can be empty)
     * @param tokenInfo - TokenInfo returned from the introspection
     * @param operation - the openapi operation
     * @return - return status to indicate if valid or not
     */
    protected Status hasValidScope(String scopeHeader, List<String> secondaryScopes, TokenInfo tokenInfo, Operation operation) {
        // validate the scope against the scopes configured in the OpenAPI spec
        if (CONFIG.isEnableVerifyScope()) {
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
                String scope = tokenInfo.getScope();
                if(scope != null) {
                    primaryScopes = Arrays.asList(scope.split(" "));
                }

                if (!matchedScopes(primaryScopes, specScopes)) {
                    LOG.error("Authorization token scope is not matched.");
                    return new Status(STATUS_AUTH_TOKEN_SCOPE_MISMATCH, primaryScopes, specScopes);
                }
            }
        }
        return successMiddlewareStatus();
    }

    protected boolean matchedScopes(List<String> tokenScopes, Collection<String> specScopes) {
        boolean matched = false;
        if (specScopes != null && specScopes.size() > 0) {
            if (tokenScopes != null && tokenScopes.size() > 0) {
                for (String scope : specScopes) {
                    if (tokenScopes.contains(scope)) {
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

    /**
     * Check is the request has secondary scopes, and they are valid.
     *
     * @param exchange - current exchange
     * @param scopeSwt - the swt token that associate with a scope
     * @param secondaryScopes - Initially an empty list that is then filled with the secondary scopes if there are any.
     * @param reqPath - the request path as string
     * @param jwkServiceIds - a list of serviceIds for jwk loading
     * @param auditInfo - a map of audit info properties
     * @return - return Status to indicate valid or not.
     */
    protected Status hasValidSecondaryScopes(LightLambdaExchange exchange, String scopeSwt, List<String> secondaryScopes, String reqPath, List<String> jwkServiceIds, Map<String, Object> auditInfo) {
        if (scopeSwt != null) {
            if (LOG.isTraceEnabled())
                LOG.trace("start verifying scope token = " + scopeSwt.substring(0, 10));
            try {
                Map<String, String> headerMap = exchange.getRequest().getHeaders();
                Optional<String> optionalSwtClientId = MapUtil.getValueIgnoreCase(headerMap, CONFIG.getSwtClientIdHeader());
                Optional<String> optionalSwtClientSecret = MapUtil.getValueIgnoreCase(headerMap, CONFIG.getSwtClientSecretHeader());
                if(LOG.isTraceEnabled()) LOG.trace("header swtClientId = " + optionalSwtClientId.orElse(null) + ", header swtClientSecret = " + StringUtils.maskHalfString(optionalSwtClientSecret.orElse(null)));
                Result<TokenInfo> scopeTokenInfo = swtVerifier.verifySwt(scopeSwt, reqPath, jwkServiceIds, optionalSwtClientId.orElse(null), optionalSwtClientSecret.orElse(null));
                if(scopeTokenInfo.isFailure()) {
                    return scopeTokenInfo.getError();
                }
                TokenInfo tokenInfo = scopeTokenInfo.getResult();
                String scope = tokenInfo.getScope();
                if(scope != null) {
                    secondaryScopes.addAll(Arrays.asList(scope.split(" ")));
                    auditInfo.put(Constants.SCOPE_CLIENT_ID_STRING, tokenInfo.getClientId());
                }
            } catch (Exception e) {
                // only the ClientException is possible here.
                LOG.error("Exception", e);
                return new Status(STATUS_CLIENT_EXCEPTION, e.getMessage());
            }
        }
        return successMiddlewareStatus();
    }

    /**
     * Get authToken (JWT or SWT) from X-Scope-Token header.
     * This covers situations where there is a secondary auth token.
     *
     * @param authorization - The auth token from authorization header
     * @param headerMap - complete header map
     * @return - return either x-scope-token or the initial auth token
     */
    protected String getScopeToken(String authorization, Map<String, String> headerMap) {
        String returnToken = authorization;
        // in the gateway case, the authorization header might be a basic header for the native API or other authentication headers.
        // this will allow the Basic authentication be wrapped up with a JWT or SWT token between proxy client and proxy server for native.
        if (returnToken != null && !returnToken.substring(0, 6).equalsIgnoreCase("Bearer")) {

            // get the jwt token from the X-Scope-Token header in this case and allow the verification done with the secondary token.
            Optional<String> optionalScopeToken = MapUtil.getValueIgnoreCase(headerMap, SCOPE_TOKEN);
            if(optionalScopeToken.isPresent()) {
                returnToken = optionalScopeToken.get();
                if (LOG.isTraceEnabled() && returnToken.length() > 10)
                    LOG.trace("The replaced authorization from X-Scope-Token header = " + returnToken.substring(0, 10));
            }
        }
        return returnToken;
    }

    @Override
    public void getCachedConfigurations() {
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnableVerifySwt();
    }

    @Override
    public void register() {

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
