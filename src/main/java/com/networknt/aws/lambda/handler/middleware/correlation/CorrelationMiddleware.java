package com.networknt.aws.lambda.handler.middleware.correlation;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.LoggerKey;
import com.networknt.config.Config;
import com.networknt.correlation.CorrelationConfig;
import com.networknt.status.Status;
import com.networknt.utility.MapUtil;
import com.networknt.utility.ModuleRegistry;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;


public class CorrelationMiddleware implements MiddlewareHandler {

    private static final CorrelationConfig CONFIG = CorrelationConfig.load();
    private static final Logger LOG = LoggerFactory.getLogger(CorrelationMiddleware.class);
    private static final LightLambdaExchange.Attachable<CorrelationMiddleware> CORRELATION_ATTACHMENT_KEY = LightLambdaExchange.Attachable.createAttachable(CorrelationMiddleware.class);

    public CorrelationMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("CorrelationHandler is construct.");
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) {
        LOG.debug("CorrelationHandler.handleRequest starts.");

        // check if the cid is in the request header
        String cid = null;
        if(exchange.getRequest().getHeaders() != null) {
            Optional<String> optionalCid = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.CORRELATION);
            cid = optionalCid.orElse(null);
        } else {
            exchange.getRequest().setHeaders(new HashMap<>());
        }
        if (cid == null && CONFIG.isAutogenCorrelationID()) {
            cid = this.getUUID();
            exchange.getRequest().getHeaders().put(HeaderKey.CORRELATION, cid);
            exchange.addAttachment(CORRELATION_ATTACHMENT_KEY, cid);
            Optional<String> optionalTid = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), HeaderKey.TRACEABILITY);
            String tid = optionalTid.orElse(null);
            if (tid != null && LOG.isInfoEnabled())
                LOG.info("Associate traceability Id {} with correlation Id {}", tid, cid);
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
