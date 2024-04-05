package com.networknt.aws.lambda.listener;

import com.networknt.aws.lambda.LightLambdaExchange;

public interface LambdaResponseCompleteListener {
    void responseCompleteEvent(final LightLambdaExchange exchange);
}
