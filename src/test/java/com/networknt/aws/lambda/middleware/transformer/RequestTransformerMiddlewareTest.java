package com.networknt.aws.lambda.middleware.transformer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.handler.middleware.transformer.RequestTransformerMiddleware;
import com.networknt.reqtrans.RequestTransformerConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class RequestTransformerMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(RequestTransformerMiddlewareTest.class);
    LightLambdaExchange exchange;

    @Test
    public void testConstructor() {
        RequestTransformerMiddleware middleware = new RequestTransformerMiddleware();
        Assertions.assertNotNull(middleware);
    }

    /**
     * This is the test to change the request body by adding a new field with the rule action.
     */
    @Test
    public void testRequestTransformerMiddleware() {
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
        RequestTransformerConfig config = RequestTransformerConfig.load("reqtrans_test");
        RequestTransformerMiddleware requestTransformerMiddleware = new RequestTransformerMiddleware(config);
        requestChain.addChainable(requestTransformerMiddleware);
        requestChain.setupGroupedChain();

        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();
        requestEvent = exchange.getFinalizedRequest(false);
        String requestBody = requestEvent.getBody();
        Assertions.assertEquals("{\"id\":1,\"name\":\"dog\",\"newField\":\"newValue\"}", requestBody);
    }

    /**
     * This is the test to validate the request and return an error response directly. Used to create a customized
     * validation.
     */
    @Test
    public void testRequestTransformerValidation() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        apiGatewayProxyRequestEvent.setPath("/v2/pets");
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
        RequestTransformerConfig config = RequestTransformerConfig.load("reqtrans_test");
        RequestTransformerMiddleware requestTransformerMiddleware = new RequestTransformerMiddleware(config);
        requestChain.addChainable(requestTransformerMiddleware);
        requestChain.setupGroupedChain();

        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();
        APIGatewayProxyResponseEvent responseEvent = exchange.getFinalizedResponse(false);
        String responseBody = responseEvent.getBody();
        Assertions.assertEquals(401, responseEvent.getStatusCode());
        Assertions.assertEquals("{\"statusCode\":401,\"code\":\"ERR10001\",\"message\":\"AUTH_TOKEN_EXPIRED\",\"description\":\"Jwt token in authorization header expired\",\"severity\":\"ERROR\"}", responseBody);
    }


}
