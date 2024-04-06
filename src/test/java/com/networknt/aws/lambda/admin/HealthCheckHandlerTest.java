package com.networknt.aws.lambda.admin;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.proxy.LambdaProxy;
import org.junit.jupiter.api.Test;

class HealthCheckHandlerTest {
    @Test
    void testHealthCheck() {
        APIGatewayProxyRequestEvent requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/health");
        requestEvent.setHttpMethod("GET");
        LambdaProxy lambdaProxy = new LambdaProxy();
        Chain chain = Handler.getChain("/adm/logger@get");
        Context lambdaContext = new LambdaContext("1");
        LightLambdaExchange exchange = new LightLambdaExchange(lambdaContext, chain);
        exchange.setInitialRequest(requestEvent);
        APIGatewayProxyResponseEvent responseEvent = lambdaProxy.handleRequest(requestEvent, lambdaContext);
        System.out.println(responseEvent.toString());

    }
}
