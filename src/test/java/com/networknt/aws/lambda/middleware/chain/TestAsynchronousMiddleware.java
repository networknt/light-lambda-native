package com.networknt.aws.lambda.middleware.chain;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class TestAsynchronousMiddleware implements MiddlewareHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TestAsynchronousMiddleware.class);

    public TestAsynchronousMiddleware() {
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) throws InterruptedException {
        LOG.info("I am executing asynchronously");

        int randomSlept = ThreadLocalRandom.current().nextInt(5, 15);
        LOG.info("I will sleep a total of {} times", randomSlept);

        int slept = 0;
        while (slept < randomSlept) {
            int randomSleep = ThreadLocalRandom.current().nextInt(0, 1000);
            LOG.info("I am sleeping asynchronously for {}ms... ({})", randomSleep, slept);
            Thread.sleep(randomSleep);
            slept++;
        }

        LOG.info("I am done executing asynchronously, doing callback");
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
        return true;
    }
}
