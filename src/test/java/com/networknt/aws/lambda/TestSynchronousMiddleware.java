package com.networknt.aws.lambda;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class TestSynchronousMiddleware implements MiddlewareHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TestSynchronousMiddleware.class);

    public TestSynchronousMiddleware() {
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) throws InterruptedException {
        LOG.info("I am executing Synchronously");

        int randomSlept = ThreadLocalRandom.current().nextInt(5, 15);
        LOG.info("I will sleep a total of {} times", randomSlept);

        int slept = 0;
        while (slept < randomSlept) {
            LOG.info("I am working Synchronously... ({})", slept);
            Thread.sleep(150);
            slept++;
        }

        LOG.info("I am done executing Synchronously, doing callback");
        return this.successMiddlewareStatus();
    }

    @Override
    public void getCachedConfigurations() {

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
    public boolean isContinueOnFailure() {
        return false;
    }

    @Override
    public boolean isAudited() {
        return true;
    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }
}
