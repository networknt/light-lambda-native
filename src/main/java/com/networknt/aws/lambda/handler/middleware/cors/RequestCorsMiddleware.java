package com.networknt.aws.lambda.handler.middleware.cors;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.config.Config;
import com.networknt.cors.CorsConfig;
import com.networknt.status.Status;
import com.networknt.utility.MapUtil;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.networknt.cors.CorsHeaders.*;
import static com.networknt.cors.CorsUtil.sanitizeDefaultPort;

/**
 * This middleware is responsible for adding CORS headers to the response and return it if the request
 * is a CORS preflight request. If the preflight request has the correct origin and method, it will return
 * 200 with the correct headers. Otherwise, 403 will be returned.
 *
 * If the request is a normal request with origin header and the origin is not matched, it will return 403.
 *
 * @author Steve Hu
 *
 */
public class RequestCorsMiddleware implements MiddlewareHandler {
    static CorsConfig CONFIG;
    private static final Logger LOG = LoggerFactory.getLogger(RequestCorsMiddleware.class);
    private static final String SUC10200 = "SUC10200";
    private static final String CORS_PREFLIGHT_REQUEST_FAILED = "ERR10092";

    private List<String> allowedOrigins;
    private List<String> allowedMethods;
    private static final String ONE_HOUR_IN_SECONDS = "3600";

    public RequestCorsMiddleware() {
        CONFIG = CorsConfig.load();
        allowedOrigins = CONFIG.getAllowedOrigins();
        allowedMethods = CONFIG.getAllowedMethods();
        LOG.info("RequestCorsMiddleware is constructed");
    }

    public RequestCorsMiddleware(CorsConfig cfg) {
        CONFIG = cfg;
        allowedOrigins = CONFIG.getAllowedOrigins();
        allowedMethods = CONFIG.getAllowedMethods();
        LOG.info("RequestCorsMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("RequestCorsMiddleware.executeMiddleware starts.");
        if (!CONFIG.isEnabled()) {
            if(LOG.isTraceEnabled()) LOG.trace("RequestCorsMiddleware is not enabled.");
            return disabledMiddlewareStatus();
        }
        APIGatewayProxyRequestEvent requestEvent = exchange.getRequest();
        if(requestEvent != null) {
            if(LOG.isTraceEnabled()) LOG.trace("Request event is not null.");
            Map<String, String> requestHeaders = requestEvent.getHeaders();
            if(isCorsRequest(requestHeaders)) {
                // set the allowed origins and methods based on the path prefix.
                if (CONFIG.getPathPrefixAllowed() != null) {
                    String requestPath = requestEvent.getPath();
                    for(Map.Entry<String, Object> entry: CONFIG.getPathPrefixAllowed().entrySet()) {
                        if (requestPath.startsWith(entry.getKey())) {
                            Map endpointCorsMap = (Map) entry.getValue();
                            allowedOrigins = (List<String>) endpointCorsMap.get(CorsConfig.ALLOWED_ORIGINS);
                            allowedMethods = (List<String>) endpointCorsMap.get(CorsConfig.ALLOWED_METHODS);
                            break;
                        }
                    }
                }
                // if it is a preflight request, then handle it and return.
                if (isPreflightedRequest(requestEvent.getHttpMethod())) {
                    // it is a preflight request. Handle it and return the response.
                    return handlePreflightRequest(exchange, allowedOrigins, allowedMethods);
                } else {
                    // normal request with origin header. check the origin and reject if it is not matched.
                    String origin = matchOrigin(requestEvent, allowedOrigins);
                    if(origin == null) {
                        return new Status(CORS_PREFLIGHT_REQUEST_FAILED);
                    }
                }
            }
        }
        // need to set the response header for the normal cors request that passed the origin check. It needs
        // to be set in the response chain instead of request chain.

        if(LOG.isTraceEnabled()) LOG.trace("RequestCorsMiddleware.executeMiddleware ends.");
        return successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                CorsConfig.CONFIG_NAME,
                RequestCorsMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(CorsConfig.CONFIG_NAME),
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

    private Status handlePreflightRequest(LightLambdaExchange exchange, List<String> allowedOrigins, List<String> allowedMethods) {
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
        Map<String, String> requestHeaders = exchange.getRequest().getHeaders();
        Map<String, String> responseHeaders = new HashMap<>();
        if (MapUtil.getValueIgnoreCase(requestHeaders, ORIGIN).isPresent()) {
            if(matchOrigin(exchange.getRequest(), allowedOrigins) != null) {
                responseHeaders.put(ACCESS_CONTROL_ALLOW_ORIGIN, MapUtil.getValueIgnoreCase(requestHeaders, ORIGIN).get());
                responseHeaders.put("Vary", "Origin");
            } else {
                responseEvent.setHeaders(responseHeaders);
                responseEvent.setStatusCode(403);
                exchange.setInitialResponse(responseEvent);
                return new Status(CORS_PREFLIGHT_REQUEST_FAILED);
            }
        }
        responseHeaders.put(ACCESS_CONTROL_ALLOW_METHODS, convertToString(allowedMethods));
        Optional<String> acRequestHeaders = MapUtil.getValueIgnoreCase(requestHeaders, ACCESS_CONTROL_REQUEST_HEADERS);
        if (acRequestHeaders.isPresent()) {
            responseHeaders.put(ACCESS_CONTROL_ALLOW_HEADERS, acRequestHeaders.get());
        } else {
            responseHeaders.put(ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type, WWW-Authenticate, Authorization");
        }
        responseHeaders.put(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        responseHeaders.put(ACCESS_CONTROL_MAX_AGE, ONE_HOUR_IN_SECONDS);
        responseEvent.setHeaders(responseHeaders);
        responseEvent.setStatusCode(200);
        exchange.setInitialResponse(responseEvent);
        return new Status(SUC10200);
    }

    /**
     * Match the Origin header with the allowed origins.
     * If it doesn't match then a 403 response code is set on the response and it returns null.
     * @param requestEvent the current request event.
     * @param allowedOrigins list of sanitized allowed origins.
     * @return the first matching origin, null otherwise.
     */
    static String matchOrigin(APIGatewayProxyRequestEvent requestEvent, Collection<String> allowedOrigins) {
        Map<String, String> requestHeaders = requestEvent.getHeaders();
        Optional<String> optionalOrigin = MapUtil.getValueIgnoreCase(requestHeaders, ORIGIN);
        String origin = optionalOrigin.orElse(null);
        if(LOG.isTraceEnabled()) LOG.trace("origin from the request header = {} allowedOrigins = {}", origin, allowedOrigins);
        if (origin != null && allowedOrigins != null && !allowedOrigins.isEmpty()) {
            for (String allowedOrigin : allowedOrigins) {
                if (allowedOrigin.equalsIgnoreCase(sanitizeDefaultPort(origin))) {
                    return allowedOrigin;
                }
            }
        }
        LOG.debug("Request rejected due to HOST/ORIGIN mis-match.");
        return null;
    }

    static boolean isCorsRequest(Map<String, String> requestHeaders) {
        // all cors request will have origin header regardless it is a preflight request
        // or normal request with method other than OPTIONS.
        return MapUtil.getValueIgnoreCase(requestHeaders, ORIGIN).isPresent();
    }

    static boolean isPreflightedRequest(String requestMethod) {
        // only the preflight request will have OPTIONS method, and it should be checked
        // after it is confirmed a cors request with origin header.
        return "OPTIONS".equalsIgnoreCase(requestMethod);
    }

    static String convertToString(List<String> list) {
        return String.join(",", list);
    }
}
