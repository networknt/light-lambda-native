package com.networknt.aws.lambda.handler.middleware.cors;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.cors.CorsConfig;
import com.networknt.status.Status;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.networknt.cors.CorsHeaders.*;

/** Apply the request's validated origin/policy snapshot after downstream response headers exist. */
public class ResponseCorsMiddleware implements MiddlewareHandler {
    @Override
    public Status execute(LightLambdaExchange exchange) {
        RequestCorsMiddleware.AcceptedOrigin accepted = exchange.getAttachment(RequestCorsMiddleware.ACCEPTED_ORIGIN);
        if (accepted == null && !CorsConfig.load().isEnabled()) return disabledMiddlewareStatus();
        APIGatewayProxyResponseEvent response = exchange.getResponse();
        if (response == null) return successMiddlewareStatus();
        Map<String, String> headers = new LinkedHashMap<>();
        if (response.getHeaders() != null) headers.putAll(response.getHeaders());
        addVaryOrigin(headers);
        if (accepted != null) {
            putHeader(headers, ACCESS_CONTROL_ALLOW_ORIGIN, accepted.origin());
            putHeader(headers, ACCESS_CONTROL_ALLOW_METHODS, RequestCorsMiddleware.convertToString(accepted.methods()));
            putHeader(headers, ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        }
        response.setHeaders(headers);
        return successMiddlewareStatus();
    }

    private static void addVaryOrigin(Map<String, String> headers) {
        String vary = headers.entrySet().stream().filter(entry -> "Vary".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue).filter(Objects::nonNull).collect(Collectors.joining(", "));
        boolean covered = Arrays.stream(vary.split(",")).map(String::trim)
                .anyMatch(token -> token.equals("*") || token.equalsIgnoreCase("Origin"));
        putHeader(headers, "Vary", covered ? vary : vary.isBlank() ? "Origin" : vary + ", Origin");
    }

    private static void putHeader(Map<String, String> headers, String name, String value) {
        headers.keySet().removeIf(key -> name.equalsIgnoreCase(key));
        headers.put(name, value);
    }

    @Override
    public boolean isEnabled() {
        return CorsConfig.load().isEnabled();
    }
}
