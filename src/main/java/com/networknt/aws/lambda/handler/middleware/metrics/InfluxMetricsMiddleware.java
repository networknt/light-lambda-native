package com.networknt.aws.lambda.handler.middleware.metrics;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InfluxMetricsMiddleware extends AbstractMetricsMiddleware {
    private static final Logger LOG = LoggerFactory.getLogger(InfluxMetricsMiddleware.class);

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isDebugEnabled()) LOG.debug("InfluxMetricsMiddleware.execute starts.");
        return null;
    }
}
