package com.networknt.aws.lambda.handler.middleware.metrics;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.app.LambdaAppConfig;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.metrics.APMAgentReporter;
import com.networknt.metrics.MetricsConfig;
import com.networknt.metrics.TimeSeriesDbSender;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import io.dropwizard.metrics.Clock;
import io.dropwizard.metrics.MetricFilter;
import io.dropwizard.metrics.MetricName;
import io.dropwizard.metrics.MetricRegistry;
import io.dropwizard.metrics.broadcom.APMEPAgentSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public class APMMetricsMiddleware extends AbstractMetricsMiddleware {
    static final Logger logger = LoggerFactory.getLogger(APMMetricsMiddleware.class);
    // this is the indicator to start the reporter and construct the common tags. It cannot be static as
    // the currentPort and currentAddress are not available during the handler initialization.
    private boolean firstTime = true;
    private long startTime;

    public APMMetricsMiddleware() {
        MetricsConfig config = MetricsConfig.load();
        if(config.getIssuerRegex() != null) {
            pattern = Pattern.compile(config.getIssuerRegex());
        }
        if(logger.isDebugEnabled()) logger.debug("ApmMetricsMiddleware is constructed!");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        MetricsConfig config = MetricsConfig.load();
        LambdaAppConfig appConfig = LambdaAppConfig.load();
        if (firstTime) {
            commonTags.put("api", appConfig.getLambdaAppId());
//            commonTags.put("env", );
//            commonTags.put("addr", Server.currentAddress);
//            commonTags.put("port", "" + (ServerConfig.getInstance().isEnableHttps() ? Server.currentHttpsPort : Server.currentHttpPort));
//            InetAddress inetAddress = Util.getInetAddress();
//            commonTags.put("host", inetAddress == null ? "unknown" : inetAddress.getHostName()); // will be container id if in docker.
            if (logger.isDebugEnabled()) {
                logger.debug(commonTags.toString());
            }

            try {
                TimeSeriesDbSender sender =
                        new APMEPAgentSender(config.getServerProtocol(), config.getServerHost(), config.getServerPort(), config.getServerPath(), appConfig.getLambdaAppId(),  config.getProductName());
                APMAgentReporter reporter = APMAgentReporter
                        .forRegistry(registry)
                        .convertRatesTo(TimeUnit.SECONDS)
                        .convertDurationsTo(TimeUnit.MILLISECONDS)
                        .filter(MetricFilter.ALL)
                        .build(sender);
                reporter.start(config.getReportInMinutes(), TimeUnit.MINUTES);

                logger.info("apmmetrics is enabled and reporter is started");
            } catch (MalformedURLException e) {
                logger.error("apmmetrics has failed to initialize APMEPAgentSender", e);
            }

            // reset the flag so that this block will only be called once.
            firstTime = false;
        }

        if(exchange.isRequestInProgress()) {
            startTime = Clock.defaultClock().getTick();
        }

        exchange.addResponseCompleteListener(finalExchange -> {
            Map<String, Object> auditInfo = (Map<String, Object>)finalExchange.getAttachment(AUDIT_ATTACHMENT_KEY);
            if(logger.isTraceEnabled()) logger.trace("auditInfo = {}", auditInfo);
            if (auditInfo != null && !auditInfo.isEmpty()) {
                Map<String, String> tags = new HashMap<>();
                tags.put("endpoint", (String) auditInfo.get(Constants.ENDPOINT_STRING));
                String clientId = auditInfo.get(Constants.CLIENT_ID_STRING) != null ? (String) auditInfo.get(Constants.CLIENT_ID_STRING) : "unknown";
                if(logger.isTraceEnabled()) logger.trace("clientId = {}", clientId);
                tags.put("clientId", clientId);
                // scope client id will only be available if two token is used. For example, authorization code flow.
                if (config.isSendScopeClientId()) {
                    tags.put("scopeClientId", auditInfo.get(Constants.SCOPE_CLIENT_ID_STRING) != null ? (String) auditInfo.get(Constants.SCOPE_CLIENT_ID_STRING) : "unknown");
                }
                // caller id is the calling serviceId that is passed from the caller. It is not always available but some organizations enforce it.
                if (config.isSendCallerId()) {
                    tags.put("callerId", auditInfo.get(Constants.CALLER_ID_STRING) != null ? (String) auditInfo.get(Constants.CALLER_ID_STRING) : "unknown");
                }
                if (config.isSendIssuer()) {
                    String issuer = (String) auditInfo.get(Constants.ISSUER_CLAIMS);
                    if (issuer != null) {
                        // we need to send issuer as a tag. Do we need to apply regex to extract only a part of the issuer?
                        if(config.getIssuerRegex() != null) {
                            Matcher matcher = pattern.matcher(issuer);
                            if (matcher.find()) {
                                String iss = matcher.group(1);
                                if(logger.isTraceEnabled()) logger.trace("Extracted issuer {} from Original issuer {] is sent.", iss, issuer);
                                tags.put("issuer", iss != null ? iss : "unknown");
                            }
                        } else {
                            if(logger.isTraceEnabled()) logger.trace("Original issuer {} is sent.", issuer);
                            tags.put("issuer", issuer);
                        }
                    }
                }
                MetricName metricName = new MetricName("response_time");
                metricName = metricName.tagged(commonTags);
                metricName = metricName.tagged(tags);
                long time = Clock.defaultClock().getTick() - startTime;
                registry.getOrAdd(metricName, MetricRegistry.MetricBuilder.TIMERS).update(time, TimeUnit.NANOSECONDS);
                if(logger.isTraceEnabled())
                    logger.trace("metricName = {} commonTags = {} tags = {}", metricName, JsonMapper.toJson(commonTags), JsonMapper.toJson(tags));
                incCounterForStatusCode(finalExchange.getFinalizedResponse(true).getStatusCode(), commonTags, tags);
            } else {
                // when we reach here, it will be in light-gateway so no specification is loaded on the server and also the security verification is failed.
                // we need to come up with the endpoint at last to ensure we have some meaningful metrics info populated.
                logger.error("auditInfo is null or empty. Please move the path prefix handler to the top of the handler chain after metrics.");
            }
        });
        return successMiddlewareStatus();
    }
}
