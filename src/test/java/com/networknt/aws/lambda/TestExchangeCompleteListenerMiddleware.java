package com.networknt.aws.lambda;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.listener.LambdaResponseCompleteListener;
import com.networknt.aws.lambda.LightLambdaExchange.Attachable;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TestExchangeCompleteListenerMiddleware implements MiddlewareHandler {
    private final Logger LOG = LoggerFactory.getLogger(TestExchangeCompleteListenerMiddleware.class);
    public static final Attachable<TestExchangeCompleteListenerMiddleware> TEST_ATTACHMENT = Attachable.createAttachable(TestExchangeCompleteListenerMiddleware.class);
    @Override
    public Status execute(LightLambdaExchange exchange) {
        exchange.addResponseCompleteListener(new LambdaResponseCompleteListener() {
            @Override
            public void responseCompleteEvent(LightLambdaExchange exchange) {
                // other logic here...
                LOG.info("Executing after the response is complete.");

                // put some random object in attachments...
                exchange.addAttachment(TEST_ATTACHMENT, new Object());
            }
        });

        return successMiddlewareStatus();
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
