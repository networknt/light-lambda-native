package com.networknt.aws.lambda.middleware.traceability;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.aws.lambda.handler.middleware.traceability.TraceabilityMiddleware;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.traceability.TraceabilityConfig;
import com.networknt.utility.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

class TraceabilityMiddlewareTest {
    private static final Logger LOG = LoggerFactory.getLogger(TraceabilityMiddlewareTest.class);

    @Test
    void test() {
        var requestEvent = TestUtils.createTestRequestEvent();

        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put(HeaderKey.TRACEABILITY, "123-123-123");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();


        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        TraceabilityMiddleware traceabilityMiddleware = new TraceabilityMiddleware();
        traceabilityMiddleware.execute(exchange);
        requestEvent = exchange.getRequest();
        Assertions.assertNotNull(requestEvent);

        // X-Traceability-Id should be added to the exchange as an attachment.
        String traceabilityId = (String) exchange.getAttachment(TraceabilityMiddleware.TRACEABILITY_ATTACHMENT_KEY);
        Assertions.assertEquals("123-123-123", traceabilityId);
    }
}
