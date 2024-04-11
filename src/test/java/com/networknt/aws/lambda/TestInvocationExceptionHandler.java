package com.networknt.aws.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.status.Status;

public class TestInvocationExceptionHandler implements MiddlewareHandler {
    @Override
    public Status execute(LightLambdaExchange exchange) {
        APIGatewayProxyRequestEvent finalizedRequest = exchange.getFinalizedRequest(false);
        // ... invoke a function
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
        responseEvent.setStatusCode(500);
        exchange.setInitialResponse(responseEvent);

        return new Status("ERR10086", "some-function");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void register() {

    }

    @Override
    public void reload() {

    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }

    @Override
    public boolean isContinueOnFailure() {
        return false;
    }

    @Override
    public boolean isAudited() {
        return false;
    }

    @Override
    public void getCachedConfigurations() {

    }
}
