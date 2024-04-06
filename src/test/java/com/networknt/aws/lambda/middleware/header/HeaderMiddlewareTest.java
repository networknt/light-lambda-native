package com.networknt.aws.lambda.middleware.header;

import com.amazonaws.services.lambda.runtime.Context;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.header.HeaderMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.header.HeaderConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class HeaderMiddlewareTest {

    @Test
    void testHeaderRemoveUpdate() {
        var requestEvent = TestUtils.createTestRequestEvent();

        // add a request header so that it can be removed by the middleware
        requestEvent.getHeaders().put("header1", "Header1Value");
        requestEvent.getHeaders().put("header2", "Header2Value");
        requestEvent.getHeaders().put("key1", "key1Old");
        requestEvent.getHeaders().put("key2", "key2Old");

        requestEvent.getHeaders().put("headerA", "HeaderAValue");
        requestEvent.getHeaders().put("headerB", "HeaderAValue");
        requestEvent.getHeaders().put("keyA", "keyAOld");
        requestEvent.getHeaders().put("keyB", "keyBOld");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");

        requestEvent.setPath("/v1/pets");
        requestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        HeaderConfig headerConfig = HeaderConfig.load("header_test");
        HeaderMiddleware headerMiddleware = new HeaderMiddleware(headerConfig);
        headerMiddleware.execute(exchange);
        requestEvent = exchange.getRequest();

        // header1 and header2 should be removed from the request headers
        Assertions.assertNull(requestEvent.getHeaders().get("header1"));
        Assertions.assertNull(requestEvent.getHeaders().get("header2"));

        // key1 and key2 should be updated in the request headers
        Assertions.assertEquals("value1", requestEvent.getHeaders().get("key1"));
        Assertions.assertEquals("value2", requestEvent.getHeaders().get("key2"));

        // headerA and headerB should be removed from the request headers
        Assertions.assertNull(requestEvent.getHeaders().get("headerA"));
        Assertions.assertNull(requestEvent.getHeaders().get("headerB"));

        // keyA and keyB should be updated in the request headers
        Assertions.assertEquals("valueA", requestEvent.getHeaders().get("keyA"));
        Assertions.assertEquals("valueB", requestEvent.getHeaders().get("keyB"));
    }
}
