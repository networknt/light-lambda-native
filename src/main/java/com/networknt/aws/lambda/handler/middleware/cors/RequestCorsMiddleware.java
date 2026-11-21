package com.networknt.aws.lambda.handler.middleware.cors;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.cors.CorsConfig;
import com.networknt.status.Status;
import com.networknt.utility.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.networknt.cors.CorsHeaders.*;

/** Validate CORS requests with the shared compiled cors-config policy. */
public class RequestCorsMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RequestCorsMiddleware.class);
    private static final String SUC10200 = "SUC10200";
    private static final String CORS_PREFLIGHT_REQUEST_FAILED = "ERR10092";
    private static final String ONE_HOUR_IN_SECONDS = "3600";

    static final LightLambdaExchange.Attachable<AcceptedOrigin> ACCEPTED_ORIGIN =
            LightLambdaExchange.Attachable.createAttachable(AcceptedOrigin.class);
    record AcceptedOrigin(String origin, List<String> methods) { }
    private record OriginHeader(boolean present, String value) { }

    public RequestCorsMiddleware() {
        CorsConfig.load();
        LOG.info("RequestCorsMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        CorsConfig config = CorsConfig.load();
        if (!config.isEnabled()) return disabledMiddlewareStatus();
        APIGatewayProxyRequestEvent request = exchange.getRequest();
        if (request == null) return successMiddlewareStatus();
        OriginHeader header = originHeader(request);
        if (!header.present) return successMiddlewareStatus();
        CorsConfig.Policy policy = config.policyForPath(request.getPath() == null ? "/" : request.getPath());
        if (header.value == null || !policy.origins().matches(header.value)) {
            if (isPreflightedRequest(request.getHttpMethod())) {
                exchange.setInitialResponse(new APIGatewayProxyResponseEvent().withIsBase64Encoded(false)
                        .withStatusCode(403).withHeaders(Map.of("Vary", "Origin")));
            }
            return new Status(CORS_PREFLIGHT_REQUEST_FAILED);
        }
        AcceptedOrigin accepted = new AcceptedOrigin(header.value, policy.methods());
        exchange.addAttachment(ACCEPTED_ORIGIN, accepted);
        if (isPreflightedRequest(request.getHttpMethod())) return handlePreflightRequest(exchange, accepted);
        return successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return CorsConfig.load().isEnabled();
    }

    private Status handlePreflightRequest(LightLambdaExchange exchange, AcceptedOrigin accepted) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(ACCESS_CONTROL_ALLOW_ORIGIN, accepted.origin);
        headers.put("Vary", "Origin");
        headers.put(ACCESS_CONTROL_ALLOW_METHODS, convertToString(accepted.methods));
        Map<String, String> requestHeaders = exchange.getRequest().getHeaders();
        String requestedHeaders = requestHeaders == null ? null
                : MapUtil.getValueIgnoreCase(requestHeaders, ACCESS_CONTROL_REQUEST_HEADERS).orElse(null);
        headers.put(ACCESS_CONTROL_ALLOW_HEADERS, requestedHeaders == null
                ? "Content-Type, WWW-Authenticate, Authorization" : requestedHeaders);
        headers.put(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        headers.put(ACCESS_CONTROL_MAX_AGE, ONE_HOUR_IN_SECONDS);
        exchange.setInitialResponse(new APIGatewayProxyResponseEvent().withIsBase64Encoded(false)
                .withStatusCode(200).withHeaders(headers));
        return new Status(SUC10200);
    }

    private static OriginHeader originHeader(APIGatewayProxyRequestEvent request) {
        List<String> singles = originValues(request.getHeaders());
        List<List<String>> multiples = originValues(request.getMultiValueHeaders());
        boolean present = !singles.isEmpty() || !multiples.isEmpty();
        if (!present || singles.size() > 1 || multiples.size() > 1) return new OriginHeader(present, null);
        if (!multiples.isEmpty()) {
            List<String> values = multiples.getFirst();
            if (values == null || values.size() != 1) return new OriginHeader(true, null);
            String value = values.getFirst();
            return new OriginHeader(true, singles.isEmpty() || Objects.equals(singles.getFirst(), value) ? value : null);
        }
        return new OriginHeader(true, singles.getFirst());
    }

    private static <T> List<T> originValues(Map<String, T> headers) {
        if (headers == null) return List.of();
        return headers.entrySet().stream().filter(entry -> ORIGIN.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).toList();
    }

    static boolean isPreflightedRequest(String method) {
        return "OPTIONS".equalsIgnoreCase(method);
    }

    static String convertToString(List<String> list) {
        return String.join(",", list);
    }
}
