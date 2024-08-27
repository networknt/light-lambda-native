package com.networknt.aws.lambda.admin;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.app.LambdaApp;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.handler.chain.Chain;
import org.junit.jupiter.api.Test;

public class CacheExplorerHandlerTest {
    @Test
    public void testGetCacheJwk() {
        APIGatewayProxyRequestEvent requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/adm/cache/jwk");
        requestEvent.setHttpMethod("GET");
        LambdaApp lambdaApp = new LambdaApp();
        Chain chain = Handler.getChain(requestEvent);
        Context lambdaContext = new LambdaContext("1");
        LightLambdaExchange exchange = new LightLambdaExchange(lambdaContext, chain);
        exchange.setInitialRequest(requestEvent);
        APIGatewayProxyResponseEvent responseEvent = lambdaApp.handleRequest(requestEvent, lambdaContext);
        System.out.println(responseEvent.toString());
    }

}