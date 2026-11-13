package com.networknt.aws.lambda.middleware.cors;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.handler.middleware.cors.RequestCorsMiddleware;
import com.networknt.aws.lambda.handler.middleware.cors.ResponseCorsMiddleware;
import com.networknt.config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CorsPolicyIntegrationTest {
    private Map<String, Object> original;

    @BeforeEach
    void saveConfig() {
        original = Config.getInstance().getJsonMapConfig("cors");
    }

    @AfterEach
    void restoreConfig() {
        if (original == null) Config.getInstance().clearConfigCache("cors");
        else Config.getInstance().putInConfigCache("cors", original);
    }

    private static void configure() {
        Config.getInstance().putInConfigCache("cors", Map.of("enabled", true,
                "allowedOrigins", List.of("https://*.example.com", "capacitor://localhost", "https://example.org:443"),
                "allowedMethods", List.of("GET", "PUT"), "pathPrefixAllowed",
                Map.of("/private", Map.of("allowedOrigins", List.of("https://trusted.example.com"), "allowedMethods", List.of("GET")))));
    }

    @Test
    void sharedCompiledPolicyAcceptsWildcardsCustomSchemesAndNormalizedPorts() {
        configure();
        RequestCorsMiddleware middleware = new RequestCorsMiddleware();
        for (String method : List.of("GET", "OPTIONS")) {
            for (String origin : List.of("https://app.example.com", "https://team.app.example.com:443", "capacitor://localhost", "https://example.org")) {
                LightLambdaExchange exchange = exchange("/public", method, origin);
                assertEquals(200, middleware.execute(exchange).getStatusCode(), origin);
                if (method.equals("OPTIONS")) assertEquals(origin, exchange.getResponse().getHeaders().get("Access-Control-Allow-Origin"));
            }
        }
    }

    @Test
    void perPathPolicyReplacesGlobalAndRejectsInvalidOrUntrustedOrigins() {
        configure();
        RequestCorsMiddleware middleware = new RequestCorsMiddleware();
        for (String method : List.of("GET", "OPTIONS")) {
            for (String origin : List.of("https://example.com", "https://badexample.com", "https://example.com.attacker.net",
                    "http://app.example.com", "https://app.example.com:8443", "https://app.example.com/", "null")) {
                assertEquals(403, middleware.execute(exchange("/public", method, origin)).getStatusCode(), origin);
            }
            assertEquals(403, middleware.execute(exchange("/private", method, "https://app.example.com")).getStatusCode());
            assertEquals(200, middleware.execute(exchange("/private", method, "https://trusted.example.com")).getStatusCode());
        }
    }

    @Test
    void responseUsesValidatedRequestSnapshotAndPreservesBackendVary() {
        configure();
        LightLambdaExchange exchange = exchange("/public", "GET", "https://app.example.com");
        assertEquals(200, new RequestCorsMiddleware().execute(exchange).getStatusCode());
        Config.getInstance().putInConfigCache("cors", Map.of("enabled", true,
                "allowedOrigins", List.of("https://other.example.org"), "allowedMethods", List.of("POST")));
        exchange.setInitialResponse(new APIGatewayProxyResponseEvent().withStatusCode(200)
                .withHeaders(new LinkedHashMap<>(Map.of("Vary", "Accept-Encoding"))));
        assertEquals(200, new ResponseCorsMiddleware().execute(exchange).getStatusCode());
        Map<String, String> headers = exchange.getResponse().getHeaders();
        assertEquals("https://app.example.com", headers.get("Access-Control-Allow-Origin"));
        assertEquals("true", headers.get("Access-Control-Allow-Credentials"));
        assertEquals("GET,PUT", headers.get("Access-Control-Allow-Methods"));
        assertTrue(headers.get("Vary").contains("Accept-Encoding"));
        assertTrue(headers.get("Vary").contains("Origin"));
    }

    @Test
    void duplicateOrConflictingOriginRepresentationsAreRejected() {
        configure();
        RequestCorsMiddleware middleware = new RequestCorsMiddleware();
        for (String method : List.of("GET", "OPTIONS")) {
            LightLambdaExchange duplicate = exchange("/public", method, "https://app.example.com");
            duplicate.getRequest().getHeaders().put("origin", "https://app.example.com");
            assertEquals(403, middleware.execute(duplicate).getStatusCode());
            LightLambdaExchange multiple = exchange("/public", method, "https://app.example.com");
            multiple.getRequest().setMultiValueHeaders(Map.of("Origin", List.of("https://app.example.com", "https://evil.net")));
            assertEquals(403, middleware.execute(multiple).getStatusCode());
            LightLambdaExchange conflicting = exchange("/public", method, "https://app.example.com");
            conflicting.getRequest().setMultiValueHeaders(Map.of("Origin", List.of("https://other.example.com")));
            assertEquals(403, middleware.execute(conflicting).getStatusCode());
            LightLambdaExchange consistent = exchange("/public", method, "https://app.example.com");
            consistent.getRequest().setMultiValueHeaders(Map.of("oRiGiN", List.of("https://app.example.com")));
            assertEquals(200, middleware.execute(consistent).getStatusCode());
            LightLambdaExchange multiOnly = exchange("/public", method, "https://app.example.com");
            multiOnly.getRequest().setHeaders(null);
            multiOnly.getRequest().setMultiValueHeaders(Map.of("origin", List.of("https://app.example.com")));
            assertEquals(200, middleware.execute(multiOnly).getStatusCode());
        }
    }

    @Test
    void middlewareReloadsPoliciesAndNoOriginResponsesKeepCacheVariation() {
        configure();
        RequestCorsMiddleware middleware = new RequestCorsMiddleware();
        assertEquals(200, middleware.execute(exchange("/public", "GET", "https://app.example.com")).getStatusCode());
        Config.getInstance().putInConfigCache("cors", Map.of("enabled", true,
                "allowedOrigins", List.of("https://other.example.org"), "allowedMethods", List.of("GET")));
        assertEquals(403, middleware.execute(exchange("/public", "GET", "https://app.example.com")).getStatusCode());
        assertEquals(200, middleware.execute(exchange("/public", "GET", "https://other.example.org")).getStatusCode());
        for (String vary : List.of("Accept-Encoding", "*", "origin")) {
            LightLambdaExchange noOrigin = exchange("/public", "GET", "https://app.example.com");
            noOrigin.getRequest().setHeaders(Map.of("Access-Control-Request-Method", "PUT"));
            assertEquals(200, middleware.execute(noOrigin).getStatusCode());
            noOrigin.setInitialResponse(new APIGatewayProxyResponseEvent().withStatusCode(200)
                    .withHeaders(Map.of("vary", vary)));
            assertEquals(200, new ResponseCorsMiddleware().execute(noOrigin).getStatusCode());
            Map<String, String> headers = noOrigin.getResponse().getHeaders();
            assertNull(headers.get("Access-Control-Allow-Origin"));
            assertTrue(headers.get("Vary").contains(vary));
            if (vary.equals("Accept-Encoding")) assertTrue(headers.get("Vary").contains("Origin"));
            else assertEquals(vary, headers.get("Vary"));
        }
    }

    private static LightLambdaExchange exchange(String path, String method, String origin) {
        LightLambdaExchange exchange = new LightLambdaExchange(null, new Chain());
        exchange.setInitialRequest(new APIGatewayProxyRequestEvent().withPath(path).withHttpMethod(method)
                .withHeaders(new LinkedHashMap<>(Map.of("Origin", origin))));
        return exchange;
    }
}
