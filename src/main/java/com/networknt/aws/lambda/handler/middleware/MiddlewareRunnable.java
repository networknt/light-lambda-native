package com.networknt.aws.lambda.handler.middleware;

import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.chain.ChainLinkCallback;

public class MiddlewareRunnable implements Runnable {

    private final LambdaHandler middlewareHandler;
    private final LightLambdaExchange exchange;

    private final ChainLinkCallback callback;

    public MiddlewareRunnable(LambdaHandler middlewareHandler, LightLambdaExchange exchange, ChainLinkCallback callback) {
        this.middlewareHandler = middlewareHandler;
        this.exchange = exchange;
        this.callback = callback;
    }

    @Override
    public void run() {
        try {
            var status = middlewareHandler.execute(exchange);
            this.callback.callback(this.exchange, status);

        } catch (Throwable e) {
            this.callback.exceptionCallback(this.exchange, e);
        }

    }
}
