package com.networknt.aws.lambda.handler.middleware.metrics;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.config.JsonMapper;
import com.networknt.metrics.JVMMetricsDbReporter;
import com.networknt.metrics.MetricsConfig;
import com.networknt.metrics.TimeSeriesDbSender;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import io.dropwizard.metrics.MetricFilter;
import io.dropwizard.metrics.MetricName;
import io.dropwizard.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public abstract class AbstractMetricsMiddleware implements MiddlewareHandler {
    static final Logger logger = LoggerFactory.getLogger(AbstractMetricsMiddleware.class);
    // The metrics.yml configuration that supports reload.
    public static MetricsConfig config;
    static Pattern pattern;
    // The structure that collect all the metrics entries. Even others will be using this structure to inject.
    public static final MetricRegistry registry = new MetricRegistry();
    public Map<String, String> commonTags = new HashMap<>();

    public AbstractMetricsMiddleware() {
    }


    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    public void createJVMMetricsReporter(final TimeSeriesDbSender sender) {
        JVMMetricsDbReporter jvmReporter = new JVMMetricsDbReporter(new MetricRegistry(), sender, "jvm-reporter",
                MetricFilter.ALL, TimeUnit.SECONDS, TimeUnit.MILLISECONDS, commonTags);
        jvmReporter.start(config.getReportInMinutes(), TimeUnit.MINUTES);
    }

    public void incCounterForStatusCode(int statusCode, Map<String, String> commonTags, Map<String, String> tags) {
        MetricName metricName = new MetricName("request").tagged(commonTags).tagged(tags);
        registry.getOrAdd(metricName, MetricRegistry.MetricBuilder.COUNTERS).inc();
        if (statusCode >= 200 && statusCode < 400) {
            metricName = new MetricName("success").tagged(commonTags).tagged(tags);
            registry.getOrAdd(metricName, MetricRegistry.MetricBuilder.COUNTERS).inc();
        } else if (statusCode == 401 || statusCode == 403) {
            metricName = new MetricName("auth_error").tagged(commonTags).tagged(tags);
            registry.getOrAdd(metricName, MetricRegistry.MetricBuilder.COUNTERS).inc();
        } else if (statusCode >= 400 && statusCode < 500) {
            metricName = new MetricName("request_error").tagged(commonTags).tagged(tags);
            registry.getOrAdd(metricName, MetricRegistry.MetricBuilder.COUNTERS).inc();
        } else if (statusCode >= 500) {
            metricName = new MetricName("server_error").tagged(commonTags).tagged(tags);
            registry.getOrAdd(metricName, MetricRegistry.MetricBuilder.COUNTERS).inc();
        }
    }

    /**
     * This is the method that is used for all other handlers to inject its metrics info to the real metrics handler impl.
     *
     * @param exchange           the LightLambdaExchange that is used to get the auditInfo to collect the metrics tag.
     * @param startTime          the start time passed in to calculate the response time.
     * @param metricsName        the name of the metrics that is collected.
     * @param endpoint           the endpoint that is used to collect the metrics. It is optional and only provided by the external handlers.
     */
    public void injectMetrics(LightLambdaExchange exchange, long startTime, String metricsName, String endpoint) {
        Map<String, Object> auditInfo = (Map<String, Object>)exchange.getAttachment(AUDIT_ATTACHMENT_KEY);
        if(logger.isTraceEnabled()) logger.trace("auditInfo = " + auditInfo);
        Map<String, String> tags = new HashMap<>();
        if (auditInfo != null) {
            // for external handlers, the endpoint must be unknown in the auditInfo. If that is the case, use the endpoint passed in.
            if (endpoint != null) {
                tags.put(Constants.ENDPOINT_STRING, endpoint);
            } else {
                tags.put(Constants.ENDPOINT_STRING, (String) auditInfo.get(Constants.ENDPOINT_STRING));
            }
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
        } else {
            // for MRAS and Salesforce handlers that do not have auditInfo in the exchange as they may be called anonymously.
            tags.put(Constants.ENDPOINT_STRING, endpoint == null ? "unknown" : endpoint);
            tags.put("clientId", "unknown");
            if (config.isSendScopeClientId()) {
                tags.put("scopeClientId", "unknown");
            }
            if (config.isSendCallerId()) {
                tags.put("callerId", "unknown");
            }
            if (config.isSendIssuer()) {
                tags.put("issuer", "unknown");
            }
        }
        MetricName metricName = new MetricName(metricsName);
        metricName = metricName.tagged(commonTags);
        metricName = metricName.tagged(tags);
        long time = System.nanoTime() - startTime;
        registry.getOrAdd(metricName, MetricRegistry.MetricBuilder.TIMERS).update(time, TimeUnit.NANOSECONDS);
        if(logger.isTraceEnabled())
            logger.trace("metricName = {} commonTags = {} tags = {}", metricName, JsonMapper.toJson(commonTags), JsonMapper.toJson(tags));
        // the metrics handler will collect the status code metrics and increase the counter. Here we don't want to increase it again.
        // incCounterForStatusCode(httpServerExchange.getStatusCode(), commonTags, tags);
    }
}
