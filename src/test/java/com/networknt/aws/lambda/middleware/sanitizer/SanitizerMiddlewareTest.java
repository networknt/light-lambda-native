package com.networknt.aws.lambda.middleware.sanitizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.sanitizer.SanitizerMiddleware;
import com.networknt.config.JsonMapper;

import com.networknt.utility.MapUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SanitizerMiddlewareTest {
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

        Chain requestChain = new Chain();
        SanitizerMiddleware sanitizerMiddleware = new SanitizerMiddleware("sanitizer_test");
        requestChain.addChainable(sanitizerMiddleware);
        requestChain.setFinalized(true);
        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();
        requestEvent = exchange.getFinalizedRequest(false);
        Map<String, String> headerMapResult = requestEvent.getHeaders();
        Optional<String> optionalParam = MapUtil.getValueIgnoreCase(headerMapResult, "param");
        // works on both linux and Windows due to EncodeWrapper
        optionalParam.ifPresent(s -> Assertions.assertTrue(s.contains("<script>alert(\\'header test\\')</script>")));
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

        Chain requestChain = new Chain();
        SanitizerMiddleware sanitizerMiddleware = new SanitizerMiddleware("sanitizer_test");
        requestChain.addChainable(sanitizerMiddleware);
        requestChain.setFinalized(true);
        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();

        requestEvent = exchange.getFinalizedRequest(false);
        String bodyResult = requestEvent.getBody();
        Map<String, Object> map = JsonMapper.string2Map(bodyResult);
        // works on both linux and Windows due to EncodeWrapper
        Assertions.assertEquals("<script>alert(\\'test\\')</script>", map.get("key"));
    }

    @Test
    public void testSanitizerMiddlewareInvalidJsonBody() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Content-Type", "application/json");
        apiGatewayProxyRequestEvent.setHeaders(headerMap);
        // invalid JSON: unquoted string value for "field"
        String body = "{\n    \"field\": a\n}";
        apiGatewayProxyRequestEvent.setBody(body);
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        Chain requestChain = new Chain();
        SanitizerMiddleware sanitizerMiddleware = new SanitizerMiddleware("sanitizer_test");
        requestChain.addChainable(sanitizerMiddleware);
        requestChain.setFinalized(true);
        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();

        APIGatewayProxyResponseEvent responseEvent = exchange.getFinalizedResponse(false);
        Assertions.assertNotNull(responseEvent);
        Assertions.assertEquals(400, responseEvent.getStatusCode());
        Assertions.assertTrue(responseEvent.getBody().contains("ERR10015"));
    }

    @Test
    public void testSanitizerMiddlewareInvalidJsonArrayBody() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("Content-Type", "application/json");
        apiGatewayProxyRequestEvent.setHeaders(headerMap);
        // invalid JSON array: unquoted string value for "field"
        String body = "[ {\"field\": a} ]";
        apiGatewayProxyRequestEvent.setBody(body);
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        Chain requestChain = new Chain();
        SanitizerMiddleware sanitizerMiddleware = new SanitizerMiddleware("sanitizer_test");
        requestChain.addChainable(sanitizerMiddleware);
        requestChain.setFinalized(true);
        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();

        APIGatewayProxyResponseEvent responseEvent = exchange.getFinalizedResponse(false);
        Assertions.assertNotNull(responseEvent);
        Assertions.assertEquals(400, responseEvent.getStatusCode());
        Assertions.assertTrue(responseEvent.getBody().contains("ERR10015"));
    }

    @Test
    public void testSanitizerMiddlewareNonJsonBodyPassesThrough() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        // a plain, non-JSON body must not be rejected by the new parse guard.
        String body = "hello";
        apiGatewayProxyRequestEvent.setBody(body);
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        Chain requestChain = new Chain();
        SanitizerMiddleware sanitizerMiddleware = new SanitizerMiddleware("sanitizer_test");
        requestChain.addChainable(sanitizerMiddleware);
        requestChain.setFinalized(true);
        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();

        // the body is untouched and the chain completes successfully (no error response).
        requestEvent = exchange.getFinalizedRequest(false);
        Assertions.assertEquals("hello", requestEvent.getBody());
    }

}
