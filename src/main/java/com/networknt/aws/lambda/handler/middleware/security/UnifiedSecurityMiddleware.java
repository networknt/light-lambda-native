package com.networknt.aws.lambda.handler.middleware.security;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.security.SecurityConfig;
import com.networknt.status.Status;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnifiedSecurityMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(UnifiedSecurityMiddleware.class);
    private static final SecurityConfig CONFIG = SecurityConfig.load(SecurityConfig.CONFIG_NAME);

    public UnifiedSecurityMiddleware() {
        if(LOG.isInfoEnabled()) LOG.info("UnifiedSecurityMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
        throw new NotImplementedException();
    }

    @Override
    public void getCachedConfigurations() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isEnabled() {
        throw new NotImplementedException();
    }

    @Override
    public void register() {
        throw new NotImplementedException();
    }

    @Override
    public void reload() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isContinueOnFailure() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isAudited() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isAsynchronous() {
        throw new NotImplementedException();
    }

}
