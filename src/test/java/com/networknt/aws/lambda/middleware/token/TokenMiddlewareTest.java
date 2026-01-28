package com.networknt.aws.lambda.middleware.token;

import com.amazonaws.services.lambda.runtime.Context;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.middleware.token.TokenMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.router.middleware.TokenConfig;
import com.networknt.utility.MapUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class TokenMiddlewareTest {

    /**
     * Test the TokenMiddleware with a request that does not have a service_id header. The handler
     * will be skipped. The Authorization header should not be added to the request.
     */
    @Test
    public void testTokenMiddlewareWithoutServiceId() {
        var requestEvent = TestUtils.createTestRequestEvent();

        // add a request header so that it can be removed by the middleware
        // requestEvent.getHeaders().put("service_id", "com.networknt.petstore-1.0.0");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");
        MapUtil.delValueIgnoreCase(requestEvent.getHeaders(), HeaderKey.AUTHORIZATION);

        requestEvent.setPath("/v1/pets");
        requestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        TokenMiddleware tokenMiddleware = new TokenMiddleware("token_test");
        tokenMiddleware.execute(exchange);
        requestEvent = exchange.getRequest();

        Assertions.assertNull(requestEvent.getHeaders().get("Authorization"));
    }

    /**
     * Test the TokenMiddleware with a wrong request path that is not configured to
     * have run this handler. Even there is a service_id in the header, the handler
     * is still skipped. The Authorization header should not be added to the request.
     */
    @Test
    public void testTokenMiddlewareWrongPath() {
        var requestEvent = TestUtils.createTestRequestEvent();

        // add a request header so that it can be removed by the middleware
        requestEvent.getHeaders().put("service_id", "com.networknt.petstore-1.0.0");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");
        requestEvent.getHeaders().remove(HeaderKey.AUTHORIZATION);

        requestEvent.setPath("/v1/wrong");
        requestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        TokenMiddleware tokenMiddleware = new TokenMiddleware("token_test");
        tokenMiddleware.execute(exchange);
        requestEvent = exchange.getRequest();

        Assertions.assertNull(requestEvent.getHeaders().get("Authorization"));
    }

    /**
     * This is the test to get the real token based on the configuration. We have service_id in the header
     * and the path is in the configuration for the token middleware. The Authorization header should be added.
     *
     * Since we are using the live OAuth 2.0 provider, we have to disable this test as we cannot reveal the
     * client_id and client_secret in the test config.
     *
     * Since we have removed the original Authorization header, the new token will be added to the Authorization
     * header.
     */
    @Disabled
    @Test
    public void testTokenMiddlewareWithIdRightPath() {
        var requestEvent = TestUtils.createTestRequestEvent();

        // add a request header so that it can be removed by the middleware
        requestEvent.getHeaders().put("service_id", "com.networknt.petstore-1.0.0");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");
        requestEvent.getHeaders().remove(HeaderKey.AUTHORIZATION);

        requestEvent.setPath("/v1/pets");
        requestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        TokenConfig tokenConfig = TokenConfig.load("token_test");
        TokenMiddleware tokenMiddleware = new TokenMiddleware("token_test");
        tokenMiddleware.execute(exchange);
        requestEvent = exchange.getRequest();

        Assertions.assertNotNull(requestEvent.getHeaders().get(HeaderKey.AUTHORIZATION));
        Assertions.assertNull(requestEvent.getHeaders().get(HeaderKey.SCOPE_TOKEN));

    }

    /**
     * This is the test to get the real token based on the configuration. We have service_id in the header
     * and the path is in the configuration for the token middleware. The Authorization header should be added.
     *
     * Since we are using the live OAuth 2.0 provider, we have to disable this test as we cannot reveal the
     * client_id and client_secret in the test config.
     *
     * Since we have removed the original Authorization header, the new token will be added to the Authorization
     * header.
     */
    @Disabled
    @Test
    public void testTokenMiddlewareWithIdRightPathToken() {
        var requestEvent = TestUtils.createTestRequestEvent();

        // add a request header so that it can be removed by the middleware
        requestEvent.getHeaders().put("service_id", "com.networknt.petstore-1.0.0");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");
        // do not remove the token from the Authorization header.
        // requestEvent.getHeaders().remove(HeaderKey.AUTHORIZATION);

        requestEvent.setPath("/v1/pets");
        requestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        TokenMiddleware tokenMiddleware = new TokenMiddleware("token_test");
        tokenMiddleware.execute(exchange);
        requestEvent = exchange.getRequest();

        Assertions.assertNotNull(requestEvent.getHeaders().get(HeaderKey.AUTHORIZATION));
        Assertions.assertNotNull(requestEvent.getHeaders().get(HeaderKey.SCOPE_TOKEN));
    }

}
