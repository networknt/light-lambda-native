package com.networknt.aws.lambda.middleware.validator;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.sanitizer.SanitizerMiddleware;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.aws.lambda.handler.middleware.validator.ValidatorMiddleware;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ValidatorMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(ValidatorMiddlewareTest.class);
    LightLambdaExchange exchange;

    @Test
    public void testConstructor() {
        OpenApiMiddleware openApiMiddleware = new OpenApiMiddleware();
        ValidatorMiddleware middleware = new ValidatorMiddleware();
        Assertions.assertNotNull(middleware);
    }

    @Test
    public void testSanitizerMiddlewareHeader() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        apiGatewayProxyRequestEvent.setPath("/v1/pets");
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("X-Traceability-Id", "abc");
        apiGatewayProxyRequestEvent.setHeaders(headerMap);
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        Chain requestChain = new Chain(false);
        OpenApiMiddleware openApiMiddleware = new OpenApiMiddleware();
        requestChain.addChainable(openApiMiddleware);
        ValidatorMiddleware validatorMiddleware = new ValidatorMiddleware();
        requestChain.addChainable(validatorMiddleware);
        requestChain.setupGroupedChain();

        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setRequest(requestEvent);
        this.exchange.executeChain();

        APIGatewayProxyResponseEvent responseEvent = exchange.getResponse();
        Assertions.assertNotNull(responseEvent);
        Assertions.assertEquals(400, responseEvent.getStatusCode());
        LOG.info("responseStatus: " + responseEvent.getStatusCode());
    }

}