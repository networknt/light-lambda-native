package com.networknt.aws.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.chain.Chain;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.networknt.aws.lambda.TestExchangeCompleteListenerMiddleware.TEST_ATTACHMENT;

class LightLambdaExchangeTest {

    @Test
    void exchangeStateHappyPath() {
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

        var testAsynchronousMiddleware = new TestAsynchronousMiddleware();
        var testInvocationHandler = new TestInvocationHandler();

        Chain requestChain = new Chain(false);

        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testInvocationHandler);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.setupGroupedChain();

        LightLambdaExchange exchange = new LightLambdaExchange(lambdaContext, requestChain);

        /* Request should NOT be in progress without setting the initial request data. */
        Assertions.assertFalse(exchange.isRequestInProgress());


        /* Request should be in progress with the initial request data set. */
        exchange.setInitialRequest(requestEvent);
        Assertions.assertTrue(exchange.isRequestInProgress());

        /* Request should be complete after executing the chain and the response should be labeled as in-progress. */
        exchange.executeChain();
        Assertions.assertTrue(exchange.isRequestComplete());
        Assertions.assertTrue(exchange.isResponseInProgress());

        /* Response should be complete after terminating writes on the response. */
        APIGatewayProxyResponseEvent response = exchange.getFinalizedResponse(false);
        Assertions.assertTrue(exchange.isResponseComplete());
        Assertions.assertNotNull(response);
    }

    @Test
    void exchangeStateHandlerExceptionTest() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        InvocationResponse invocation = InvocationResponse.builder().requestId("12345").event(apiGatewayProxyRequestEvent).build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        var testAsynchronousMiddleware = new TestAsynchronousMiddleware();
        var testInvocationExceptionHandler = new TestInvocationExceptionHandler();
        Chain requestChain = new Chain(false);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testInvocationExceptionHandler);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.setupGroupedChain();
        LightLambdaExchange exchange = new LightLambdaExchange(lambdaContext, requestChain);

        /* Request should NOT be in progress without setting the initial request data. */
        Assertions.assertFalse(exchange.isRequestInProgress());

        /* Request should be in progress with the initial request data set. */
        exchange.setInitialRequest(requestEvent);
        Assertions.assertTrue(exchange.isRequestInProgress());

        /* Request should be complete after executing the chain and the response should be labeled as in-progress. */
        exchange.executeChain();
        Assertions.assertTrue(exchange.hasFailedState());
        Assertions.assertTrue(exchange.isResponseInProgress());

        /* Response should be complete after terminating writes on the response. */
        APIGatewayProxyResponseEvent response = exchange.getFinalizedResponse(false);
        Assertions.assertTrue(exchange.isResponseComplete());
        Assertions.assertNotNull(response);

        System.out.println(response);
    }

    @Test
    void exchangeCompleteListener() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();
        InvocationResponse invocation = InvocationResponse.builder().requestId("12345").event(apiGatewayProxyRequestEvent).build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        var testExchangeCompleteListenerMiddleware = new TestExchangeCompleteListenerMiddleware();
        var testInvocationHandler = new TestInvocationHandler();

        Chain requestChain = new Chain(false);
        requestChain.addChainable(testExchangeCompleteListenerMiddleware);
        requestChain.addChainable(testInvocationHandler);
        requestChain.setupGroupedChain();
        LightLambdaExchange exchange = new LightLambdaExchange(lambdaContext, requestChain);
        exchange.setInitialRequest(requestEvent);
        exchange.executeChain();

        var res = exchange.getFinalizedResponse(false);
        Assertions.assertNotNull(exchange.getAttachment(TEST_ATTACHMENT));
    }

    @Test
    public void testExchangeState() {
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

        var testAsynchronousMiddleware = new TestAsynchronousMiddleware();
        var testInvocationHandler = new TestInvocationHandler();

        Chain requestChain = new Chain(false);

        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testInvocationHandler);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.addChainable(testAsynchronousMiddleware);
        requestChain.setupGroupedChain();

        LightLambdaExchange exchange = new LightLambdaExchange(lambdaContext, requestChain);

        /* Request should NOT be in progress without setting the initial request data. */
        Assertions.assertFalse(exchange.isRequestInProgress());


        /* Request should be in progress with the initial request data set. */
        exchange.setInitialRequest(requestEvent);
        Assertions.assertTrue(exchange.isRequestInProgress());
        exchange.getFinalizedRequest(false);
        // set the response
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(200);
        exchange.setInitialResponse(response);
        Assertions.assertEquals(22, exchange.getState());
        Assertions.assertTrue(exchange.isRequestComplete());

    }
}
