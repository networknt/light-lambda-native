package com.networknt.aws.lambda.middleware.limit;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.limit.LimitMiddleware;
import com.networknt.limit.LimitConfig;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LimitMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(LimitMiddlewareTest.class);
    LightLambdaExchange exchange;

    @Test
    public void testLimitMiddlewareMoreRequests() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        Chain requestChain = new Chain(false);
        LimitConfig limitConfig = LimitConfig.load("limit_test");
        LimitMiddleware limitMiddleware = new LimitMiddleware(limitConfig);
        requestChain.addChainable(limitMiddleware);
        requestChain.setupGroupedChain();

        for(int i =  0; i < 12; i++) {
            this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
            this.exchange.setInitialRequest(requestEvent);
            this.exchange.executeChain();
        }
        APIGatewayProxyResponseEvent responseEvent = exchange.getResponse();
        Assertions.assertNotNull(responseEvent);
        Assertions.assertEquals(429, responseEvent.getStatusCode());
        LOG.info("responseStatus: " + responseEvent.getStatusCode());
    }

}
