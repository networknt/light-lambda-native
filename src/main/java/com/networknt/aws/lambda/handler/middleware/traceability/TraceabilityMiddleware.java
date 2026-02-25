package com.networknt.aws.lambda.handler.middleware.traceability;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.LoggerKey;
import com.networknt.config.Config;
import com.networknt.status.Status;
import com.networknt.traceability.TraceabilityConfig;
import com.networknt.utility.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Optional;

public class TraceabilityMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TraceabilityMiddleware.class);
    public static final LightLambdaExchange.Attachable<TraceabilityMiddleware> TRACEABILITY_ATTACHMENT_KEY = LightLambdaExchange.Attachable.createAttachable(TraceabilityMiddleware.class);

    public TraceabilityMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("TraceabilityMiddleware is constructed");
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) {
        TraceabilityConfig config = TraceabilityConfig.load();
        if (!config.isEnabled())
            return disabledMiddlewareStatus();

        if (LOG.isDebugEnabled())
            LOG.debug("TraceabilityMiddleware.executeMiddleware starts.");

        String tid = null;
        if(exchange.getRequest() != null && exchange.getRequest().getHeaders() != null) {
            Optional<String> tidOptional = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.TRACEABILITY);
            tid = tidOptional.orElse(null);
        }

        if (tid != null) {
            MDC.put(LoggerKey.TRACEABILITY, tid);
            exchange.addAttachment(TRACEABILITY_ATTACHMENT_KEY, tid);
        }

        if (LOG.isDebugEnabled())
            LOG.debug("TraceabilityMiddleware.executeMiddleware ends.");

        return successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return TraceabilityConfig.load().isEnabled();
    }
}
