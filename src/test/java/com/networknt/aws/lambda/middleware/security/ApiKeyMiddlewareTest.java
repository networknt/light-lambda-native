package com.networknt.aws.lambda.middleware.security;

import com.amazonaws.services.lambda.runtime.Context;
import com.networknt.apikey.ApiKeyConfig;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.middleware.security.ApiKeyMiddleware;
import com.networknt.status.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ApiKeyMiddlewareTest {
    /**
     * Test with a path that is in the apikey pathPrefixAuths definition. We are expecting return OK.
     */
    @Test
    public void testWithRightPathRightApiKey() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/test1");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put("x-gateway-apikey", "abcdefg");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        ApiKeyConfig config = ApiKeyConfig.load("apikey_test");
        ApiKeyMiddleware apiKeyMiddleware = new ApiKeyMiddleware(config);
        Status status = apiKeyMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(200, status.getStatusCode());
    }

    /**
     * Test with a path that is in the apikey pathPrefixAuths definition. However, the apikey header name
     * is mixed case, and we are expecting return OK.
     *
     */
    @Test
    public void testWithRightPathUpperApiKey() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/test1");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put("X-Gateway-apikey", "abcdefg");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        ApiKeyConfig config = ApiKeyConfig.load("apikey_test");
        ApiKeyMiddleware apiKeyMiddleware = new ApiKeyMiddleware(config);
        Status status = apiKeyMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(200, status.getStatusCode());
    }

    /**
     * Test with a path that is not in the apikey pathPrefixAuths definition. Even there is
     * no apikey in the header, we are expecting return OK.
     *
     */
    @Test
    public void testWithNotConfiguredPath() throws Exception {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/test3");
        // add the X-Traceability-Id to the header
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        ApiKeyConfig config = ApiKeyConfig.load("apikey_test");
        ApiKeyMiddleware apiKeyMiddleware = new ApiKeyMiddleware(config);
        Status status = apiKeyMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(200, status.getStatusCode());
    }

    /**
     * Test with a path that is in the second entry of apikey pathPrefixAuths. This is to define
     * another header name for the same path. Note that we use Authorization to overwrite the
     * default JWT token as well.
     *
     */
    @Test
    public void testWithSecondRightPathRightApiKey() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/test1");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put("Authorization", "xyz");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        ApiKeyConfig config = ApiKeyConfig.load("apikey_test");
        ApiKeyMiddleware apiKeyMiddleware = new ApiKeyMiddleware(config);
        Status status = apiKeyMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(200, status.getStatusCode());
    }

    /**
     * Test with a path that is in the apikey pathPrefixAuths definition. However, the apikey is wrong.
     */
    @Test
    public void testWithRightPathWrongKey() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/test1");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put("x-gateway-apikey", "wrong-key");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        ApiKeyConfig config = ApiKeyConfig.load("apikey_test");
        ApiKeyMiddleware apiKeyMiddleware = new ApiKeyMiddleware(config);
        Status status = apiKeyMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10075", status.getCode());
    }

    /**
     * The path is configured for the apikey verification, however, the apikey was put into a wrong header
     * that is not the defined header in the configuration. Expecting an error return with code ERR10057.
     */
    @Test
    public void testWithRightPathWrongHeader() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/test1");
        requestEvent.getHeaders().put("wrong-header", "abcdefg");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        ApiKeyConfig config = ApiKeyConfig.load("apikey_test");
        ApiKeyMiddleware apiKeyMiddleware = new ApiKeyMiddleware(config);
        Status status = apiKeyMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10075", status.getCode());
    }
}
