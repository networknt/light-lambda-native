package com.networknt.aws.lambda.handler.middleware.correlation;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.LoggerKey;
import com.networknt.config.Config;
import com.networknt.correlation.CorrelationConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.ByteBuffer;
import java.util.UUID;


public class CorrelationMiddleware implements MiddlewareHandler {

    private static final CorrelationConfig CONFIG = CorrelationConfig.load();
    private static final Logger LOG = LoggerFactory.getLogger(CorrelationMiddleware.class);
    private static final LightLambdaExchange.Attachable<CorrelationMiddleware> CORRELATION_ATTACHMENT_KEY = LightLambdaExchange.Attachable.createMiddlewareAttachable(CorrelationMiddleware.class);

    public CorrelationMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("CorrelationHandler is construct.");
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) throws InterruptedException {
        LOG.debug("CorrelationHandler.handleRequest starts.");

        // check if the cid is in the request header
        var cid = exchange.getRequest().getHeaders().get(HeaderKey.CORRELATION);

        if (cid == null && CONFIG.isAutogenCorrelationID()) {
            cid = this.getUUID();
            exchange.getRequest().getHeaders().put(HeaderKey.CORRELATION, cid);
            exchange.addRequestAttachment(CORRELATION_ATTACHMENT_KEY, cid);
            var tid = exchange.getRequest().getHeaders().get(HeaderKey.TRACEABILITY);

            if (tid != null && LOG.isInfoEnabled())
                LOG.info("Associate traceability Id " + tid + " with correlation Id " + cid);

        }

        if (cid != null)
            MDC.put(LoggerKey.CORRELATION, cid);

        LOG.debug("CorrelationHandler.handleRequest ends.");
        return successMiddlewareStatus();
    }

    private String getUUID() {
        UUID id = UUID.randomUUID();
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(id.getMostSignificantBits());
        bb.putLong(id.getLeastSignificantBits());
        return Base64.encodeBase64URLSafeString(bb.array());
    }

    @Override
    public void getCachedConfigurations() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                CorrelationConfig.CONFIG_NAME,
                CorrelationMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(CorrelationConfig.CONFIG_NAME),
                null
        );
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
        return true;
    }


}
