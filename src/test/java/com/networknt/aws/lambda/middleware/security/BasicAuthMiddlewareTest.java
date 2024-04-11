package com.networknt.aws.lambda.middleware.security;

import com.amazonaws.services.lambda.runtime.Context;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.utility.Constants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public class BasicAuthMiddlewareTest {
    @Test
    public void testBasicAuthMiddleware() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v1/pets");
        requestEvent.setBody("{\"id\": 1, \"name\": \"dog\"}");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();

        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        OpenApiMiddleware openApiMiddleware = new OpenApiMiddleware();
        openApiMiddleware.execute(exchange);
        requestEvent = exchange.getRequest();
        Assertions.assertNotNull(requestEvent);
        // make sure that the auditInfo attachment is in the exchange and there are two keys.

        Map<String, Object> auditInfo = (Map<String, Object>)exchange.getAttachment(AUDIT_ATTACHMENT_KEY);;
        Assertions.assertNotNull(auditInfo);
        String endpoint = (String)auditInfo.get(Constants.ENDPOINT_STRING);
        Assertions.assertNotNull(endpoint);
        Assertions.assertEquals("/pets@post", endpoint);
        Object openApiOperation = auditInfo.get(Constants.OPENAPI_OPERATION_STRING);
        Assertions.assertNotNull(openApiOperation);

    }
}
