package com.networknt.aws.lambda.handler.middleware.audit;

import com.networknt.audit.AuditConfig;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.listener.LambdaResponseCompleteListener;
import com.networknt.aws.lambda.proxy.LambdaProxyConfig;
import com.networknt.config.Config;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * This middleware is responsible for auditing the request and response. it will wire in the end of the response chain and output the
 * information collected in the audit attachment in the exchange to an audit log file. There are several middleware handlers will be
 * responsible to update the attachment in the exchange.
 *
 */
public class AuditMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AuditMiddleware.class);
    private static AuditConfig CONFIG;
    public static final LightLambdaExchange.Attachable<AuditMiddleware> AUDIT_ATTACHMENT_KEY = LightLambdaExchange.Attachable.createAttachable(AuditMiddleware.class);
    static final String INVALID_CONFIG_VALUE_CODE = "ERR10060";
    private String serviceId;

    private DateTimeFormatter DATE_TIME_FORMATTER;

    public AuditMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("AuditHandler is constructed.");
        CONFIG = AuditConfig.load();
        // get the serviceId from the proxy config
        LambdaProxyConfig proxyConfig = (LambdaProxyConfig) Config.getInstance().getJsonObjectConfig(LambdaProxyConfig.CONFIG_NAME, LambdaProxyConfig.class);
        serviceId = proxyConfig.getLambdaAppId();
        String timestampFormat = CONFIG.getTimestampFormat();
        if (!StringUtils.isBlank(timestampFormat)) {
            try {
                DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(timestampFormat)
                        .withZone(ZoneId.systemDefault());
            } catch (IllegalArgumentException e) {
                LOG.error(new Status(INVALID_CONFIG_VALUE_CODE, timestampFormat, "timestampFormat", "audit.yml").toString());
            }
        }
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
        if(LOG.isDebugEnabled()) LOG.debug("AuditMiddleware.execute starts.");
        // as there is no way to write a separate audit log file in Lambda, we will skip this handler.

        exchange.addResponseCompleteListener(finalExchange -> {
            var attachments = finalExchange.getAttachments();
            for (var attachment : attachments.entrySet()) {
                LOG.info("key: '{}', value: '{}'", attachment.getKey(), attachment.getValue());
            }
        });

        if(LOG.isDebugEnabled()) LOG.debug("AuditMiddleware.execute ends.");
        return successMiddlewareStatus();
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
                AuditConfig.CONFIG_NAME,
                AuditMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(AuditConfig.CONFIG_NAME),
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
