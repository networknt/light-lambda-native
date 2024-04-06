package com.networknt.aws.lambda.listener;

import com.networknt.aws.lambda.LightLambdaExchange;

public interface LambdaExchangeFailureListener {
    void exchangeFailedEvent(final LightLambdaExchange exchange);
}
