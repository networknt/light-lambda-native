package com.networknt.aws.lambda.middleware.sanitizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.sanitizer.SanitizerMiddleware;
import com.networknt.config.JsonMapper;
import com.networknt.sanitizer.SanitizerConfig;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class SanitizerMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(SanitizerMiddlewareTest.class);
    LightLambdaExchange exchange;

    @Test
    public void testConstructor() {
        SanitizerMiddleware middleware = new SanitizerMiddleware();
        Assertions.assertNotNull(middleware);
    }

    @Test
    public void testSanitizerMiddlewareHeader() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("param", "<script>alert('header test')</script>");
        apiGatewayProxyRequestEvent.setHeaders(headerMap);
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        Chain requestChain = new Chain(false);
        SanitizerConfig sanitizerConfig = SanitizerConfig.load("sanitizer_test");
        SanitizerMiddleware sanitizerMiddleware = new SanitizerMiddleware(sanitizerConfig);
        requestChain.addChainable(sanitizerMiddleware);
        requestChain.setupGroupedChain();

        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();
        requestEvent = exchange.getFinalizedRequest();
        Map<String, String> headerMapResult = requestEvent.getHeaders();
        String param = headerMapResult.get("param");
        // works on both linux and Windows due to EncodeWrapper
        Assertions.assertTrue(param.contains("<script>alert(\\'header test\\')</script>"));
    }

    @Test
    public void testSanitizerMiddlewareBody() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        String body = "{\"key\":\"<script>alert('test')</script>\"}";
        apiGatewayProxyRequestEvent.setBody(body);
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        Chain requestChain = new Chain(false);
        SanitizerConfig sanitizerConfig = SanitizerConfig.load("sanitizer_test");
        SanitizerMiddleware sanitizerMiddleware = new SanitizerMiddleware(sanitizerConfig);
        requestChain.addChainable(sanitizerMiddleware);
        requestChain.setupGroupedChain();

        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();

        requestEvent = exchange.getFinalizedRequest();
        String bodyResult = requestEvent.getBody();
        Map<String, Object> map = JsonMapper.string2Map(bodyResult);
        // works on both linux and Windows due to EncodeWrapper
        Assertions.assertEquals("<script>alert(\\'test\\')</script>", map.get("key"));
    }

}
