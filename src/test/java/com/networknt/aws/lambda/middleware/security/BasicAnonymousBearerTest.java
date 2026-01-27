package com.networknt.aws.lambda.middleware.security;

import com.amazonaws.services.lambda.runtime.Context;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.middleware.security.BasicAuthMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.basicauth.BasicAuthConfig;
import com.networknt.status.Status;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BasicAnonymousBearerTest {

    /**
     * Test without Authorization header and wrong path. Expect 401 status code.
     */
    @Test
    public void testWithAnonymousWrongPath() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/wrong");
        // add the X-Traceability-Id to the header
        // requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("user1", "user1pass"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-anonymous");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10071", status.getCode());
    }

    /**
     * Test without Authorization header and wrong path. Expect 401 status code.
     */
    @Test
    public void testWithAnonymousRightPath() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // add the X-Traceability-Id to the header
        // requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("user1", "user1pass"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-anonymous");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(200, status.getStatusCode());
    }

    /**
     * Test without Authorization header and wrong path. Expect 401 status code.
     */
    @Test
    public void testAllowBearerAuth() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "Bearer token");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-anonymous");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(200, status.getStatusCode());
    }

    /**
     * Test without Authorization header and wrong path. Expect 401 status code.
     */
    @Test
    public void testAllowBearerAuthWrongPath() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/wrong");
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "Bearer token");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-anonymous");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10071", status.getCode());
    }

}
