package com.networknt.aws.lambda.middleware.header;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.app.LambdaApp;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.MapUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResponseHeaderMiddlewareTest {
    @Test
    void testResponseHeaderRemoveUpdate() {
        var requestEvent = TestUtils.createTestRequestEvent();

        requestEvent.setPath("/v1/pets");
        requestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        LambdaApp lambdaApp = new LambdaApp();
        APIGatewayProxyResponseEvent responseEvent = lambdaApp.handleRequest(requestEvent, lambdaContext);
        Assertions.assertNotNull(responseEvent);

        // header3 and header4 should be removed by the common rule.
        Assertions.assertTrue(MapUtil.getValueIgnoreCase(requestEvent.getHeaders(), "header3").isEmpty());
        Assertions.assertTrue(MapUtil.getValueIgnoreCase(requestEvent.getHeaders(), "header4").isEmpty());

        // key3 and key4 should be updated by the common rule.
        Assertions.assertEquals("value3", MapUtil.getValueIgnoreCase(responseEvent.getHeaders(), "key3").get());
        Assertions.assertEquals("value4", MapUtil.getValueIgnoreCase(responseEvent.getHeaders(), "key4").get());

        // headerC and headerD should be removed by the /v1/pets rule.
        Assertions.assertTrue(MapUtil.getValueIgnoreCase(responseEvent.getHeaders(), "headerC").isEmpty());
        Assertions.assertTrue(MapUtil.getValueIgnoreCase(responseEvent.getHeaders(), "headerD").isEmpty());

        // keyC and KeyD should be added by the /v1/pets rule.
        Assertions.assertEquals("valueC", responseEvent.getHeaders().get("keyC"));  // lower case
        Assertions.assertEquals("valueD", responseEvent.getHeaders().get("KeyD"));  // upper case
    }
}
