package com.networknt.aws.lambda.handler.middleware.metrics;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.app.LambdaAppConfig;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.Unit;
import com.networknt.config.Config;
import com.networknt.metrics.MetricsConfig;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public class CloudWatchMetricsMiddleware extends AbstractMetricsMiddleware {
    static final Logger logger = LoggerFactory.getLogger(CloudWatchMetricsMiddleware.class);
    public static final LambdaAppConfig LAMBDA_APP_CONFIG = (LambdaAppConfig) Config.getInstance().getJsonObjectConfig(LambdaAppConfig.CONFIG_NAME, LambdaAppConfig.class);
    static Pattern pattern;
    private long startTime;

    public CloudWatchMetricsMiddleware() {
        config = MetricsConfig.load();
        if(config.getIssuerRegex() != null) {
            pattern = Pattern.compile(config.getIssuerRegex());
        }
        ModuleRegistry.registerModule(MetricsConfig.CONFIG_NAME, CloudWatchMetricsMiddleware.class.getName(), Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(MetricsConfig.CONFIG_NAME), null);
        if(logger.isDebugEnabled()) logger.debug("CloudWatchMetricsMiddleware is constructed!");

    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(exchange.isRequestInProgress()) {
            // this is in the request chain time.
            startTime = System.currentTimeMillis();
        }

        exchange.addResponseCompleteListener(finalExchange -> {
            MetricsLogger metricsLogger = (exchange.getAttachment(METRICS_LOGGER_ATTACHMENT_KEY) != null) ? (MetricsLogger) exchange.getAttachment(METRICS_LOGGER_ATTACHMENT_KEY) : null;
            if(metricsLogger == null) {
                if(logger.isTraceEnabled()) logger.trace("metricsLogger is null, create one.");
                metricsLogger = new MetricsLogger();
                exchange.addAttachment(METRICS_LOGGER_ATTACHMENT_KEY, metricsLogger);
            }

            // this is in the response chain.
            Map<String, Object> auditInfo = (Map<String, Object>)finalExchange.getAttachment(AUDIT_ATTACHMENT_KEY);
            if(logger.isTraceEnabled()) logger.trace("auditInfo = {}", auditInfo);
            if (auditInfo != null && !auditInfo.isEmpty()) {
                Map<String, Object> tags = new HashMap<>();
                metricsLogger.putProperty("endpoint", auditInfo.get(Constants.ENDPOINT_STRING));
                String clientId = auditInfo.get(Constants.CLIENT_ID_STRING) != null ? (String) auditInfo.get(Constants.CLIENT_ID_STRING) : "unknown";
                if (logger.isTraceEnabled()) logger.trace("clientId = {}", clientId);
                metricsLogger.putProperty("clientId", clientId);
                // scope client id will only be available if two token is used. For example, authorization code flow.
                if (config.isSendScopeClientId()) {
                    metricsLogger.putProperty("scopeClientId", auditInfo.get(Constants.SCOPE_CLIENT_ID_STRING) != null ? auditInfo.get(Constants.SCOPE_CLIENT_ID_STRING) : "unknown");
                }
                // caller id is the calling serviceId that is passed from the caller. It is not always available but some organizations enforce it.
                if (config.isSendCallerId()) {
                    metricsLogger.putProperty("callerId", auditInfo.get(Constants.CALLER_ID_STRING) != null ? auditInfo.get(Constants.CALLER_ID_STRING) : "unknown");
                }
                if (config.isSendIssuer()) {
                    String issuer = (String) auditInfo.get(Constants.ISSUER_CLAIMS);
                    if (issuer != null) {
                        // we need to send issuer as a tag. Do we need to apply regex to extract only a part of the issuer?
                        if (config.getIssuerRegex() != null) {
                            Matcher matcher = pattern.matcher(issuer);
                            if (matcher.find()) {
                                String iss = matcher.group(1);
                                if (logger.isTraceEnabled())
                                    logger.trace("Extracted issuer {} from Original issuer {} is sent.", iss, issuer);
                                metricsLogger.putProperty("issuer", iss != null ? iss : "unknown");
                            }
                        } else {
                            if (logger.isTraceEnabled()) logger.trace("Original issuer {} is sent.", issuer);
                            metricsLogger.putProperty("issuer", issuer);
                        }
                    }
                }
            } else {
                // when we reach here, it will be in an instance without specification is loaded on the server and also
                // the security verification is failed or disabled.
                // we need to come up with the endpoint at last to ensure we have some meaningful metrics info populated.
                metricsLogger.putProperty(Constants.ENDPOINT_STRING, "unknown");
                metricsLogger.putProperty("clientId", "unknown");
                if (config.isSendScopeClientId()) {
                    metricsLogger.putProperty("scopeClientId", "unknown");
                }
                if (config.isSendCallerId()) {
                    metricsLogger.putProperty("callerId", "unknown");
                }
                if (config.isSendIssuer()) {
                    metricsLogger.putProperty("issuer", "unknown");
                }
                logger.error("auditInfo is null or empty. Please move the path prefix handler to the top of the handler chain after metrics.");
            }

            long time = System.currentTimeMillis() - startTime;
            metricsLogger.putMetric("response_time", time, Unit.MILLISECONDS);
            incCounterForStatusCode(metricsLogger, finalExchange.getFinalizedResponse(true).getStatusCode());
            metricsLogger.putDimensions(DimensionSet.of("Service", "Aggregator"));
            metricsLogger.putProperty("api", LAMBDA_APP_CONFIG.getLambdaAppId());
            metricsLogger.flush();
        });
        return successMiddlewareStatus();
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

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                MetricsConfig.CONFIG_NAME,
                CloudWatchMetricsMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(MetricsConfig.CONFIG_NAME),
                null
        );

    }

    @Override
    public void reload() {

    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }
}
