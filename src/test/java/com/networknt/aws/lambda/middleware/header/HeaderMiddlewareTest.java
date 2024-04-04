package com.networknt.aws.lambda.middleware.header;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.header.HeaderMiddleware;
import com.networknt.aws.lambda.middleware.MiddlewareTestBase;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.header.HeaderConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Testcontainers;


@Testcontainers
class HeaderMiddlewareTest extends MiddlewareTestBase {
    private static final Logger LOG = LoggerFactory.getLogger(HeaderMiddlewareTest.class);
    LightLambdaExchange exchange;

    @Test
    void test() {
        var apiGatewayProxyRequestEvent = TestUtils.createTestRequestEvent();

        // add a request header so that it can be removed by the middleware
        apiGatewayProxyRequestEvent.getHeaders().put("header1", "Header1Value");
        apiGatewayProxyRequestEvent.getHeaders().put("header2", "Header2Value");
        apiGatewayProxyRequestEvent.getHeaders().put("key1", "key1Old");
        apiGatewayProxyRequestEvent.getHeaders().put("key2", "key2Old");

        apiGatewayProxyRequestEvent.getHeaders().put("headerA", "HeaderAValue");
        apiGatewayProxyRequestEvent.getHeaders().put("headerB", "HeaderAValue");
        apiGatewayProxyRequestEvent.getHeaders().put("keyA", "keyAOld");
        apiGatewayProxyRequestEvent.getHeaders().put("keyB", "keyBOld");

        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(apiGatewayProxyRequestEvent)
                .build();
        APIGatewayProxyRequestEvent requestEvent = invocation.getEvent();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());

        Chain requestChain = new Chain(false);
        HeaderConfig headerConfig = HeaderConfig.load("header_test");
        HeaderMiddleware headerMiddleware = new HeaderMiddleware(headerConfig);
        requestChain.addChainable(headerMiddleware);
        requestChain.setupGroupedChain();

        this.exchange = new LightLambdaExchange(lambdaContext, requestChain);
        this.exchange.setInitialRequest(requestEvent);
        this.exchange.executeChain();

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

        String res = this.invokeLocalLambdaFunction(this.exchange);
        LOG.debug(res);
        Assertions.assertNotNull(res);

    }
}
