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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BasicBearerNoUserTest {
    /**
     * Test with Bearer header and wrong path. Expect 401 status code.
     */
    @Test
    public void testWithBearerWrongPath() {
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
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth-bearer");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(400, status.getStatusCode());
        Assertions.assertEquals("ERR10072", status.getCode());
    }

    /**
     * Test with Bearer header and wrong path. Expect 401 status code.
     */
    @Test
    public void testWithBearerRightPath() {
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
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth-bearer");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(400, status.getStatusCode());
        Assertions.assertEquals("ERR10072", status.getCode());
    }

}
