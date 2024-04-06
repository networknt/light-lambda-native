package com.networknt.aws.lambda.listener;

import com.networknt.aws.lambda.LightLambdaExchange;

public interface LambdaRequestCompleteListener {
    void requestCompleteEvent(final LightLambdaExchange exchange);
}
