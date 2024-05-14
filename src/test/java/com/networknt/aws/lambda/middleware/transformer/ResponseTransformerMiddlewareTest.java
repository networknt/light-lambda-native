package com.networknt.aws.lambda.middleware.transformer;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.middleware.transformer.ResponseTransformerMiddleware;
import com.networknt.aws.lambda.proxy.LambdaApp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ResponseTransformerMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(ResponseTransformerMiddlewareTest.class);
    LightLambdaExchange exchange;

    @Test
    public void testConstructor() {
        ResponseTransformerMiddleware middleware = new ResponseTransformerMiddleware();
        Assertions.assertNotNull(middleware);
    }

    /**
     * This is the test to change the request body by adding a new field with the rule action.
     */
    @Test
    public void testResponseTransformerMiddleware() {
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


        final APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        final LambdaContext lambdaContext = new LambdaContext(invocation.getRequestId());

        LambdaApp lambdaApp = new LambdaApp();
        APIGatewayProxyResponseEvent responseEvent = lambdaApp.handleRequest(requestEvent, lambdaContext);

        System.out.println(responseEvent.toString());
        String responseBody = responseEvent.getBody();
        Assertions.assertEquals("{\"id\":1,\"name\":\"doggy\",\"newField\":\"newValue\"}", responseBody);
    }
}
