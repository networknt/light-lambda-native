package com.networknt.aws.lambda.middleware.specification;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.aws.lambda.middleware.MiddlewareTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class OpenApiMiddlewareTest extends MiddlewareTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(OpenApiMiddlewareTest.class);
    LightLambdaExchange exchange;

    @Test
    public void testConstructor() {
        OpenApiMiddleware openApiMiddleware = new OpenApiMiddleware();
        Assertions.assertNotNull(openApiMiddleware);
    }

    @Test
    public void testOpenApiMiddleware() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        Chain requestChain = new Chain(false);
        OpenApiMiddleware openApiMiddleware = new OpenApiMiddleware();
        requestChain.addChainable(openApiMiddleware);
        requestChain.setupGroupedChain();

        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();

        requestEvent = exchange.getFinalizedRequest();

        String res = this.invokeLocalLambdaFunction(this.exchange);
        LOG.debug(res);
        Assertions.assertNotNull(res);
    }
}
