package com.networknt.aws.lambda.handler.middleware.metrics;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware;
import com.networknt.config.JsonMapper;
import com.networknt.metrics.JVMMetricsDbReporter;
import com.networknt.metrics.MetricsConfig;
import com.networknt.metrics.TimeSeriesDbSender;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import io.dropwizard.metrics.Metric;
import io.dropwizard.metrics.MetricFilter;
import io.dropwizard.metrics.MetricName;
import io.dropwizard.metrics.MetricRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.MetricsContext;
import software.amazon.cloudwatchlogs.emf.model.Unit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;

public abstract class AbstractMetricsMiddleware implements MiddlewareHandler {
    static final Logger logger = LoggerFactory.getLogger(AbstractMetricsMiddleware.class);
    public static final LightLambdaExchange.Attachable<AbstractMetricsMiddleware> METRICS_LOGGER_ATTACHMENT_KEY = LightLambdaExchange.Attachable.createAttachable(AbstractMetricsMiddleware.class);
    // The metrics.yml configuration that supports reload.
    public static MetricsConfig config;

    public AbstractMetricsMiddleware() {
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    public void incCounterForStatusCode(MetricsLogger metricsLogger, int statusCode) {
        metricsLogger.putMetric("request", 1, Unit.COUNT);
        if (statusCode >= 200 && statusCode < 400) {
            metricsLogger.putMetric("success", 1, Unit.COUNT);
        } else if (statusCode == 401 || statusCode == 403) {
            metricsLogger.putMetric("auth_error", 1, Unit.COUNT);
        } else if (statusCode >= 400 && statusCode < 500) {
            metricsLogger.putMetric("request_error", 1, Unit.COUNT);
        } else if (statusCode >= 500) {
            metricsLogger.putMetric("server_error", 1, Unit.COUNT);
        }
    }

    /**
     * This is the method that is used for all other handlers to inject its metrics info to the real metrics handler impl.
     *
     * @param exchange           the LightLambdaExchange that is used to get the auditInfo to collect the metrics tag.
     * @param startTime          the start time passed in to calculate the response time.
     * @param metricsName        the name of the metrics that is collected.
     */
    public void injectMetrics(LightLambdaExchange exchange, long startTime, String metricsName) {
        MetricsLogger metricsLogger = (exchange.getAttachment(METRICS_LOGGER_ATTACHMENT_KEY) != null) ? (MetricsLogger) exchange.getAttachment(METRICS_LOGGER_ATTACHMENT_KEY) : null;
        if(metricsLogger == null) {
            if(logger.isTraceEnabled()) logger.trace("metricsContext is null, create one.");
            metricsLogger = new MetricsLogger();
            exchange.addAttachment(METRICS_LOGGER_ATTACHMENT_KEY, metricsLogger);
        }
        long time = System.currentTimeMillis() - startTime;
        metricsLogger.putMetric(metricsName, time, Unit.MILLISECONDS);
        if(logger.isTraceEnabled())
            logger.trace("metricName {} is injected with time {}", metricsName, time);
    }
}
