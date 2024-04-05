package com.networknt.aws.lambda.handler.middleware.traceability;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.LoggerKey;
import com.networknt.config.Config;
import com.networknt.status.Status;
import com.networknt.traceability.TraceabilityConfig;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class TraceabilityMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(TraceabilityMiddleware.class);
    public static final LightLambdaExchange.Attachable<TraceabilityMiddleware> TRACEABILITY_ATTACHMENT_KEY = LightLambdaExchange.Attachable.createAttachable(TraceabilityMiddleware.class);
    private static TraceabilityConfig CONFIG;

    public TraceabilityMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("TraceabilityMiddleware is constructed");
        CONFIG = TraceabilityConfig.load();
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) throws InterruptedException {
        if (!CONFIG.isEnabled())
            return disabledMiddlewareStatus();

        if (LOG.isDebugEnabled())
            LOG.debug("TraceabilityMiddleware.executeMiddleware starts.");

        var tid = exchange.getRequest().getHeaders().get(HeaderKey.TRACEABILITY);

        if (tid != null) {
            MDC.put(LoggerKey.TRACEABILITY, tid);
            exchange.addAttachment(TRACEABILITY_ATTACHMENT_KEY, tid);
        }

        if (LOG.isDebugEnabled())
            LOG.debug("TraceabilityMiddleware.executeMiddleware ends.");

        return successMiddlewareStatus();
    }

    @Override
    public void getCachedConfigurations() {
        // TODO
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                TraceabilityConfig.CONFIG_NAME,
                TraceabilityMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(TraceabilityConfig.CONFIG_NAME),
                null
        );
    }

    @Override
    public void reload() {

    }

    @Override
    public boolean isContinueOnFailure() {
        return true;
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
