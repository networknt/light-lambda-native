package com.networknt.aws.lambda.handler.middleware.metrics;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetricsMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsMiddleware.class);

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
        if(LOG.isDebugEnabled()) LOG.debug("MetricsMiddleware.execute starts.");
        return null;
    }

    @Override
    public boolean isEnabled() {
        return false;
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
