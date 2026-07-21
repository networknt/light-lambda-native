package com.networknt.aws.lambda.handler.middleware.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.status.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Routing tests for {@link LambdaProxyMiddleware}.
 * <p>
 * These exercise the real middleware rather than {@code MockLambdaProxyMiddleware}, which resolves
 * functions through a plain map lookup and therefore cannot cover the matcher logic at all.
 */
public class LambdaProxyMiddlewareTest {

    private static final Map<String, String> FUNCTIONS = Map.of(
            "/v1/pets@get", "PetsGetFunction",
            "/v1/pets@post", "PetsPostFunction",
            "/v1/pets/{petId}@get", "PetsPetIdGetFunction");

    private static LightLambdaExchange exchangeFor(final String path, final String method) {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath(path);
        requestEvent.setHttpMethod(method);

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        return exchange;
    }

    @Test
    public void testResolveFunctionNameMatchesConfiguredRoute() {
        var middleware = new LambdaProxyMiddleware(FUNCTIONS);
        Assertions.assertEquals("PetsGetFunction", middleware.resolveFunctionName("/v1/pets", "get"));
        Assertions.assertEquals("PetsPostFunction", middleware.resolveFunctionName("/v1/pets", "post"));
    }

    @Test
    public void testResolveFunctionNameMatchesPathTemplate() {
        var middleware = new LambdaProxyMiddleware(FUNCTIONS);
        Assertions.assertEquals("PetsPetIdGetFunction", middleware.resolveFunctionName("/v1/pets/123", "get"));
    }

    /**
     * Regression test for the NPE reported in issue #173. The path matches a configured function but
     * no matcher is registered for the method, which used to dereference a null matcher.
     */
    @Test
    public void testResolveFunctionNameReturnsNullWhenMethodHasNoMatcher() {
        var middleware = new LambdaProxyMiddleware(FUNCTIONS);
        Assertions.assertNull(middleware.resolveFunctionName("/v1/pets", "delete"));
    }

    @Test
    public void testResolveFunctionNameReturnsNullWhenPathDoesNotMatch() {
        var middleware = new LambdaProxyMiddleware(FUNCTIONS);
        Assertions.assertNull(middleware.resolveFunctionName("/v1/unknown", "get"));
    }

    /**
     * Regression test for issue #173 at the middleware boundary. An unmapped method must produce a
     * routing failure status rather than throwing.
     */
    @Test
    public void testExecuteReturnsFailureStatusWhenMethodHasNoMatcher() {
        var middleware = new LambdaProxyMiddleware(FUNCTIONS);
        Status status = Assertions.assertDoesNotThrow(
                () -> middleware.execute(exchangeFor("/v1/pets", "DELETE")));
        Assertions.assertEquals(LambdaProxyMiddleware.FAILED_TO_INVOKE_LAMBDA, status.getCode());
        Assertions.assertTrue(status.getDescription().contains("/v1/pets@delete"),
                "expected the failure description to identify the unroutable endpoint, but was: "
                        + status.getDescription());
    }

    @Test
    public void testExecuteReturnsFailureStatusWhenPathDoesNotMatch() {
        var middleware = new LambdaProxyMiddleware(FUNCTIONS);
        Status status = Assertions.assertDoesNotThrow(
                () -> middleware.execute(exchangeFor("/v1/unknown", "GET")));
        Assertions.assertEquals(LambdaProxyMiddleware.FAILED_TO_INVOKE_LAMBDA, status.getCode());
    }
}
