package com.networknt.aws.lambda.middleware.metrics;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.proxy.LambdaProxy;
import com.networknt.aws.lambda.utility.HeaderKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class APMMetricsMiddlewareTest {
    @Test
    public void testAPMMetricsMiddleware() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v1/pets");
        requestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "12345");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        LambdaProxy lambdaProxy = new LambdaProxy();
        APIGatewayProxyResponseEvent responseEvent = lambdaProxy.handleRequest(requestEvent, lambdaContext);
        Assertions.assertNotNull(responseEvent);
    }

}
