package com.networknt.aws.lambda.handler.middleware.security;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.security.SecurityConfig;
import com.networknt.status.Status;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwtVerifyMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(SwtVerifyMiddleware.class);
    private static final SecurityConfig CONFIG = SecurityConfig.load(SecurityConfig.CONFIG_NAME);

    public SwtVerifyMiddleware() {
        if(LOG.isInfoEnabled()) LOG.info("SwtVerifyMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
        return null;
    }

    @Override
    public void getCachedConfigurations() {
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnableVerifySwt();
    }

    @Override
    public void register() {

    }

    @Override
    public void reload() {

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
    public boolean isAsynchronous() {
        return false;
    }
}
