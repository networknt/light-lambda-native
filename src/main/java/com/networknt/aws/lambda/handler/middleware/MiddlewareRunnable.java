package com.networknt.aws.lambda.handler.middleware;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.chain.ChainLinkCallback;
import com.networknt.aws.lambda.handler.middleware.router.LambdaRouterMiddleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MiddlewareRunnable implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(MiddlewareRunnable.class);

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
        if(LOG.isTraceEnabled()) LOG.trace("Executing middleware {} isResponseMiddleware {} isRequestComplete {}", this.middlewareHandler.getClass().getSimpleName(), this.middlewareHandler.isResponseMiddleware(), this.exchange.isRequestComplete());
        try {
            if(!this.middlewareHandler.isResponseMiddleware() && this.exchange.isRequestComplete()) {
                // skip the request chain if the request is completed for all request middleware.
                if(LOG.isTraceEnabled()) LOG.trace("Skipping request middleware {} as request is already complete", this.middlewareHandler.getClass().getSimpleName());
                return;
            }
            if(this.middlewareHandler.isResponseMiddleware() && this.exchange.isResponseComplete()) {
                // skip the response chain if the response is completed for all response middleware.
                if(LOG.isTraceEnabled()) LOG.trace("Skipping response middleware {} as response is already complete", this.middlewareHandler.getClass().getSimpleName());
                return;
            }
            var status = this.middlewareHandler.execute(this.exchange);
            this.callback.callback(this.exchange, status);

        } catch (Exception e) {

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            this.callback.exceptionCallback(this.exchange, e);
        }

    }
}
