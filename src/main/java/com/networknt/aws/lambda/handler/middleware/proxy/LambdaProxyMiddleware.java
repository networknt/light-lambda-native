package com.networknt.aws.lambda.handler.middleware.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.metrics.AbstractMetricsMiddleware;
import com.networknt.config.JsonMapper;
import com.networknt.metrics.MetricsConfig;
import com.networknt.status.Status;
import com.networknt.utility.PathTemplateMatcher;
import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LambdaProxyMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LambdaProxyMiddleware.class);
    private AbstractMetricsMiddleware metricsMiddleware;

    public static final String FAILED_TO_INVOKE_LAMBDA = "ERR10086";
    public static final String EXCHANGE_HAS_FAILED_STATE = "ERR10087";

    private volatile LambdaAsyncClient client;
    private volatile LambdaProxyConfig config;
    private final Map<String, PathTemplateMatcher<String>> methodToMatcherMap = new HashMap<>();

    public LambdaProxyMiddleware() {
        this.config = LambdaProxyConfig.load();
        this.client = initClient(config);
        if (config.isMetricsInjection())
            lookupMetricsMiddleware();
        populateMethodToMatcherMap(config.getFunctions());
        if (LOG.isInfoEnabled())
            LOG.info("LambdaProxyMiddleware is constructed");
    }

    private LambdaAsyncClient initClient(LambdaProxyConfig config) {
        /* Create our netty client */
        var readTimeout = config.getReadTimeout();
        var writeTimeout = config.getWriteTimeout();
        var connectionTimeout = config.getConnectionTimeout();
        LOG.debug(
                "Creating 'SdkAsyncHttpClient' with readTimeout = '{}ms' writeTimeout = '{}ms' connectionTimeout = '{}ms'",
                readTimeout,
                writeTimeout,
                connectionTimeout);
        SdkAsyncHttpClient asyncHttpClient = NettyNioAsyncHttpClient.builder()
                .readTimeout(Duration.ofMillis(readTimeout))
                .writeTimeout(Duration.ofMillis(writeTimeout))
                .connectionTimeout(Duration.ofMillis(connectionTimeout))
                .build();

        /* Add some override properties */
        var apiCallTimeout = config.getApiCallTimeout();
        var apiCallAttemptTimeout = config.getApiCallAttemptTimeout();
        LOG.debug("Creating 'ClientOverrideConfiguration' with apiCallTimeout = '{}ms' apiCallAttemptTimeout = '{}ms'",
                apiCallTimeout,
                apiCallAttemptTimeout);
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(apiCallTimeout))
                .apiCallAttemptTimeout(Duration.ofMillis(apiCallAttemptTimeout))
                .build();

        /* Build lambda client using the netty client and additional configuration */
        var builder = LambdaAsyncClient.builder().region(Region.of(config.getRegion()))
                .httpClient(asyncHttpClient)
                .overrideConfiguration(overrideConfig);
        if (!StringUtils.isEmpty(config.getEndpointOverride()))
            builder.endpointOverride(URI.create(config.getEndpointOverride()));
        return builder.build();
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        LOG.trace("LambdaProxyMiddleware.execute starts.");
        LambdaProxyConfig newConfig = LambdaProxyConfig.load();
        if (config != newConfig) {
            synchronized (this) {
                if (config != newConfig) {
                    this.config = newConfig;
                    this.client = initClient(config);
                    if (config.isMetricsInjection())
                        lookupMetricsMiddleware();
                    populateMethodToMatcherMap(config.getFunctions());
                    LOG.info("LambdaProxyConfig is reloaded.");
                }
            }
        }
        if (!exchange.hasFailedState()) {
            /* invoke lambda function */
            var path = exchange.getRequest().getPath();
            var method = exchange.getRequest().getHttpMethod().toLowerCase();
            LOG.debug("Request path: {} -- Request method: {} -- Start time: {}", path, method, System.currentTimeMillis());
            PathTemplateMatcher.PathMatchResult<String> result = methodToMatcherMap.get(method).match(path);
            if (result == null) {
                LOG.error("No lambda function found for path: {} and method: {}", path, method);
                return new Status(FAILED_TO_INVOKE_LAMBDA, path + "@" + method);
            }
            var functionName = result.getValue();
            LOG.trace("Function name: {}", functionName);
            var res = this.invokeFunction(this.client, functionName, exchange);
            if (res == null) {
                LOG.error("Failed to invoke lambda function: {}", functionName);
                return new Status(FAILED_TO_INVOKE_LAMBDA, functionName);
            }
            LOG.debug("Invoke Time - Finish: {}", System.currentTimeMillis());
            var responseEvent = JsonMapper.fromJson(res, APIGatewayProxyResponseEvent.class);
            exchange.setInitialResponse(responseEvent);
            LOG.trace("LambdaProxyMiddleware.execute ends.");
            return this.successMiddlewareStatus();
        } else {
            LOG.error("Exchange has failed state {}", exchange.getState());
            return new Status(EXCHANGE_HAS_FAILED_STATE, exchange.getState());
        }
    }

    private void populateMethodToMatcherMap(final Map<String, String> functions) {
        this.methodToMatcherMap.clear();
        for (var entry : functions.entrySet()) {
            var endpoint = entry.getKey().split("@");
            var path = endpoint[0];
            var method = endpoint[1];
            PathTemplateMatcher<String> matcher = this.methodToMatcherMap.computeIfAbsent(method,
                    k -> new PathTemplateMatcher<>());
            if (matcher.get(path) == null)
                matcher.add(path, entry.getValue());
            this.methodToMatcherMap.put(method, matcher);
        }
    }

    private String invokeFunction(
            final LambdaAsyncClient client,
            final String functionName,
            final LightLambdaExchange exchange
    ) {
        String serializedEvent = JsonMapper.toJson(exchange.getFinalizedRequest(false));
        try {
            var payload = SdkBytes.fromUtf8String(serializedEvent);
            var request = InvokeRequest.builder()
                    .functionName(functionName)
                    .logType(config.getLogType())
                    .payload(payload)
                    .build();
            long startTime = System.nanoTime();
            CompletableFuture<String> futureResponse = client.invoke(request)
                    .thenApply(res -> {
                        this.optionalMetricsInject(startTime, exchange);
                        LOG.trace("LambdaProxyMiddleware.invokeFunction response: {}", res);
                        return new String(res.payload().asByteArray(), StandardCharsets.UTF_8);
                    })
                    .exceptionally(e -> {
                        LOG.error("Error invoking lambda function: {}", functionName, e);
                        return null;
                    });
            return futureResponse.get();
        } catch (ExecutionException e) {
            LOG.error("ExecutionException", e);
        } catch (InterruptedException e) {
            LOG.error("InterruptedException", e);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    private void optionalMetricsInject(final long startTime, final LightLambdaExchange exchange) {
        if (config.isMetricsInjection()) {
            if (metricsMiddleware == null)
                lookupMetricsMiddleware();

            if (metricsMiddleware != null) {
                LOG.trace("Inject metrics for {}", config.getMetricsName());
                metricsMiddleware.injectMetrics(exchange, startTime, config.getMetricsName(), null);
            }
        }
    }

    private void lookupMetricsMiddleware() {
        // get the metrics middleware instance from the chain.
        Map<String, LambdaHandler> handlers = Handler.getHandlers();
        this.metricsMiddleware = (AbstractMetricsMiddleware) handlers.get(MetricsConfig.CONFIG_NAME);
        if (metricsMiddleware == null) {
            LOG.error("An instance of MetricsMiddleware is not configured in the handler.yml file.");
        }
    }

}
