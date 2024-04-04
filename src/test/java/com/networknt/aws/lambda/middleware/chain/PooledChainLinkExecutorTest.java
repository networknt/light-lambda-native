package com.networknt.aws.lambda.middleware.chain;

import com.networknt.aws.lambda.*;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.handler.middleware.header.HeaderMiddleware;
import com.networknt.header.HeaderConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PooledChainLinkExecutorTest {

    @Test
    void handlerEnableCheckTest() {

        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();

        apiGatewayProxyRequestEvent.getHeaders().put("header1", "Header1Value");
        apiGatewayProxyRequestEvent.getHeaders().put("header2", "Header2Value");
        apiGatewayProxyRequestEvent.getHeaders().put("key1", "key1Old");
        apiGatewayProxyRequestEvent.getHeaders().put("key2", "key2Old");

        apiGatewayProxyRequestEvent.getHeaders().put("headerA", "HeaderAValue");
        apiGatewayProxyRequestEvent.getHeaders().put("headerB", "HeaderAValue");
        apiGatewayProxyRequestEvent.getHeaders().put("keyA", "keyAOld");
        apiGatewayProxyRequestEvent.getHeaders().put("keyB", "keyBOld");

        /* create fake context + event */
        var invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        var requestEvent = invocation.getEvent();
        var lambdaContext = new LambdaContext(invocation.getRequestId());

        /* create a disabled header middleware */
        var disabledHeaderConfig = HeaderConfig.load("header_disabled_test");
        var headerDisabledHandler = new HeaderMiddleware(disabledHeaderConfig);

        var testSynchronousMiddleware = new TestSynchronousMiddleware();

        var chain = new Chain(false);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(headerDisabledHandler);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.addChainable(testSynchronousMiddleware);
        chain.setupGroupedChain();

        /* create a new exchange with the disabled header chain + fake context (LightLambdaExchange uses PooledChainLinkExecutor to execute request/response chains) */
        var exchange = new LightLambdaExchange(lambdaContext, chain);
        exchange.setInitialRequest(requestEvent);

        exchange.executeChain();

        // header1 and header2 should not be removed from the request headers
        Assertions.assertNotNull(requestEvent.getHeaders().get("header1"));
        Assertions.assertNotNull(requestEvent.getHeaders().get("header2"));

        // key1 and key2 should not be updated in the request headers
        Assertions.assertNotEquals("value1", requestEvent.getHeaders().get("key1"));
        Assertions.assertNotEquals("value2", requestEvent.getHeaders().get("key2"));

        // headerA and headerB should not be removed from the request headers
        Assertions.assertNotNull(requestEvent.getHeaders().get("headerA"));
        Assertions.assertNotNull(requestEvent.getHeaders().get("headerB"));

        // keyA and keyB should not be updated in the request headers
        Assertions.assertNotEquals("valueA", requestEvent.getHeaders().get("keyA"));
        Assertions.assertNotEquals("valueB", requestEvent.getHeaders().get("keyB"));
    }

}
