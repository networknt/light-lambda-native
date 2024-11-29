package com.networknt.aws.lambda.middleware.cors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.app.LambdaApp;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.handler.middleware.cors.RequestCorsMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.cors.CorsConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.networknt.cors.CorsHeaders.ORIGIN;

public class CorsMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(CorsMiddlewareTest.class);

    @Test
    public void testPreflightWithRightOrigin() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v1/pets");
        requestEvent.getHeaders().put(ORIGIN, "https://def.com");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");
        requestEvent.setHttpMethod("OPTIONS");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        LambdaApp lambdaApp = new LambdaApp();
        APIGatewayProxyResponseEvent responseEvent = lambdaApp.handleRequest(requestEvent, lambdaContext);
        Assertions.assertNotNull(responseEvent);

        Assertions.assertNotNull(responseEvent);
        Assertions.assertEquals(200, responseEvent.getStatusCode());
        Map<String, String> responseHeaders = responseEvent.getHeaders();
        Assertions.assertEquals("https://def.com", responseHeaders.get("Access-Control-Allow-Origin"));
        Assertions.assertEquals("true", responseHeaders.get("Access-Control-Allow-Credentials"));
        Assertions.assertEquals("GET,PUT,POST,DELETE,PATCH", responseHeaders.get("Access-Control-Allow-Methods"));
        Assertions.assertEquals("3600", responseHeaders.get("Access-Control-Max-Age"));
        Assertions.assertEquals("Content-Type, WWW-Authenticate, Authorization", responseHeaders.get("Access-Control-Allow-Headers"));
        LOG.info("responseStatus: {}", responseEvent.getStatusCode());
    }

    @Test
    public void testPreflightWithWrongOrigin() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v1/pets");
        requestEvent.getHeaders().put(ORIGIN, "https://wrong.com");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");
        requestEvent.setHttpMethod("OPTIONS");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        LambdaApp lambdaApp = new LambdaApp();
        APIGatewayProxyResponseEvent responseEvent = lambdaApp.handleRequest(requestEvent, lambdaContext);
        Assertions.assertNotNull(responseEvent);
        Assertions.assertEquals(403, responseEvent.getStatusCode());
        LOG.info("responseStatus: {}", responseEvent.getStatusCode());
    }

    @Test
    public void testNormalRequestWithRightOriginGlobal() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v1/pets");
        requestEvent.getHeaders().put(ORIGIN, "https://def.com");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");
        requestEvent.setHttpMethod("GET");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        LambdaApp lambdaApp = new LambdaApp();
        APIGatewayProxyResponseEvent responseEvent = lambdaApp.handleRequest(requestEvent, lambdaContext);
        Assertions.assertNotNull(responseEvent);
        Assertions.assertEquals(200, responseEvent.getStatusCode());
        Map<String, String> responseHeaders = responseEvent.getHeaders();
        Assertions.assertEquals("https://def.com", responseHeaders.get("Access-Control-Allow-Origin"));
        LOG.info("responseStatus: {}", responseEvent.getStatusCode());
    }

    @Test
    public void testNormalRequestWithWrongOriginGlobal() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v1/pets");
        requestEvent.getHeaders().put(ORIGIN, "https://wrong.com");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");
        requestEvent.setHttpMethod("GET");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        LambdaApp lambdaApp = new LambdaApp();
        APIGatewayProxyResponseEvent responseEvent = lambdaApp.handleRequest(requestEvent, lambdaContext);
        Assertions.assertNotNull(responseEvent);
        Assertions.assertEquals(403, responseEvent.getStatusCode());
        Map<String, String> responseHeaders = responseEvent.getHeaders();
        LOG.info("responseStatus: {}", responseEvent.getStatusCode());
    }

}
