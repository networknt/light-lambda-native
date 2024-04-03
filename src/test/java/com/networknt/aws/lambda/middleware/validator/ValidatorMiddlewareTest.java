package com.networknt.aws.lambda.middleware.validator;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.sanitizer.SanitizerMiddleware;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.aws.lambda.handler.middleware.validator.ValidatorMiddleware;
import com.networknt.config.Config;
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
    public void testBooleanJsonNode() {
        String jsonString = "false";
        try {
            JsonNode jsonNode = Config.getInstance().getMapper().readTree(jsonString);
            boolean value = jsonNode.asBoolean();
            Assertions.assertFalse(value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Test
    public void testStringJsonNode() {
        String jsonString = "abc";
        try {
            JsonNode jsonNode = Config.getInstance().getMapper().readTree(jsonString);
            String value = jsonNode.asText();
            Assertions.assertNotNull(value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testConstructor() {
        OpenApiMiddleware openApiMiddleware = new OpenApiMiddleware();
        ValidatorMiddleware middleware = new ValidatorMiddleware();
        Assertions.assertNotNull(middleware);
    }

    @Test
    public void testValidatorWithCorrectBodyTraceabilityHeader() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        apiGatewayProxyRequestEvent.setPath("/v1/pets");
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("X-Traceability-Id", "abc");
        apiGatewayProxyRequestEvent.setHeaders(headerMap);
        // set request body
        apiGatewayProxyRequestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");
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
        // no error, the response should be null.
        Assertions.assertNull(responseEvent);
    }

    @Test
    public void testValidatorWithIncorrectBodyTraceabilityHeader() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        apiGatewayProxyRequestEvent.setPath("/v1/pets");
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("X-Traceability-Id", "abc");
        apiGatewayProxyRequestEvent.setHeaders(headerMap);
        // set request body
        apiGatewayProxyRequestEvent.setBody("{\"id\": 1}");
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
        // body validation error, the response should not be null.
        Assertions.assertNotNull(responseEvent);
        Assertions.assertEquals(400, responseEvent.getStatusCode());
        LOG.info("status: " + responseEvent.getBody());
    }

    @Test
    public void testValidatorWithoutTraceabilityHeader() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        apiGatewayProxyRequestEvent.setPath("/v1/pets");
        Map<String, String> headerMap = new HashMap<>();
        //headerMap.put("X-Traceability-Id", "abc");
        apiGatewayProxyRequestEvent.setHeaders(headerMap);
        // set request body
        apiGatewayProxyRequestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");
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
        LOG.info("status: " + responseEvent.getBody());
    }

    @Test
    public void testValidatorWithCorrectBodyAndDifferentCaseTraceabilityHeader() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        apiGatewayProxyRequestEvent.setPath("/v1/pets");
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("x-Traceability-id", "abc");
        apiGatewayProxyRequestEvent.setHeaders(headerMap);
        // set request body
        apiGatewayProxyRequestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");
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
        // no error, the response should be null.
        Assertions.assertNull(responseEvent);
    }

}
