package com.networknt.aws.lambda.handler.middleware.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.metrics.AbstractMetricsMiddleware;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.metrics.MetricsConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LambdaProxyMiddleware implements MiddlewareHandler {
    private static LambdaAsyncClient client;
    private static final Logger LOG = LoggerFactory.getLogger(LambdaProxyMiddleware.class);
    private static AbstractMetricsMiddleware metricsMiddleware;

    public static final String FAILED_TO_INVOKE_LAMBDA = "ERR10086";
    public static final String EXCHANGE_HAS_FAILED_STATE = "ERR10087";

    public static final LambdaProxyConfig CONFIG = (LambdaProxyConfig) Config.getInstance().getJsonObjectConfig(LambdaProxyConfig.CONFIG_NAME, LambdaProxyConfig.class);

    static final Map<String, PathTemplateMatcher<String>> methodToMatcherMap = new HashMap<>();

    public LambdaProxyMiddleware() {
        SdkAsyncHttpClient asyncHttpClient = NettyNioAsyncHttpClient.builder()
                .readTimeout(Duration.ofMillis(CONFIG.getApiCallAttemptTimeout()))
                .writeTimeout(Duration.ofMillis(CONFIG.getApiCallAttemptTimeout()))
                .connectionTimeout(Duration.ofMillis(CONFIG.getApiCallAttemptTimeout()))
                .build();
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(CONFIG.getApiCallTimeout()))
                .apiCallAttemptTimeout(Duration.ofSeconds(CONFIG.getApiCallAttemptTimeout()))
                .build();

        var builder = LambdaAsyncClient.builder().region(Region.of(CONFIG.getRegion()))
                        .httpClient(asyncHttpClient)
                        .overrideConfiguration(overrideConfig);
        if (!StringUtils.isEmpty(CONFIG.getEndpointOverride()))
            builder.endpointOverride(URI.create(CONFIG.getEndpointOverride()));
        client = builder.build();

        if(CONFIG.isMetricsInjection()) lookupMetricsMiddleware();
        populateMethodToMatcherMap(CONFIG.getFunctions());
        if (LOG.isInfoEnabled()) LOG.info("LambdaProxyMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("LambdaProxyMiddleware.execute starts.");
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
            if(LOG.isTraceEnabled()) LOG.trace("Function name: {}", functionName);
            var res = this.invokeFunction(client, functionName, exchange);
            if (res == null) {
                LOG.error("Failed to invoke lambda function: {}", functionName);
                return new Status(FAILED_TO_INVOKE_LAMBDA, functionName);
            }
            LOG.debug("Invoke Time - Finish: {}", System.currentTimeMillis());
            var responseEvent = JsonMapper.fromJson(res, APIGatewayProxyResponseEvent.class);
            exchange.setInitialResponse(responseEvent);
            if(LOG.isTraceEnabled()) LOG.trace("LambdaProxyMiddleware.execute ends.");
            return this.successMiddlewareStatus();
        } else {
            LOG.error("Exchange has failed state {}", exchange.getState());
            return new Status(EXCHANGE_HAS_FAILED_STATE, exchange.getState());
        }
    }

    private void populateMethodToMatcherMap(Map<String, String> functions) {
        for (var entry : functions.entrySet()) {
            var endpoint = entry.getKey();
            var path = endpoint.split("@")[0];
            var method = endpoint.split("@")[1];
            PathTemplateMatcher<String> matcher = methodToMatcherMap.computeIfAbsent(method, k -> new PathTemplateMatcher<>());
            if(matcher.get(path) == null) matcher.add(path, entry.getValue());
            methodToMatcherMap.put(method, matcher);
        }
    }

    private String invokeFunction(final LambdaAsyncClient client, String functionName, final LightLambdaExchange exchange) {
        String serializedEvent = JsonMapper.toJson(exchange.getFinalizedRequest(false));
        try {
            var payload = SdkBytes.fromUtf8String(serializedEvent);
            var request = InvokeRequest.builder()
                    .functionName(functionName)
                    .logType(CONFIG.getLogType())
                    .payload(payload)
                    .build();
            long startTime = System.nanoTime();
            CompletableFuture<String> futureResponse = client.invoke(request)
                    .thenApply(res -> {
                        if(CONFIG.isMetricsInjection()) {
                            if(metricsMiddleware == null) lookupMetricsMiddleware();
                            if(metricsMiddleware != null) {
                                if (LOG.isTraceEnabled()) LOG.trace("Inject metrics for {}", CONFIG.getMetricsName());
                                metricsMiddleware.injectMetrics(exchange, startTime, CONFIG.getMetricsName(), null);
                            }
                        }
                        if (LOG.isTraceEnabled()) LOG.trace("LambdaProxyMiddleware.invokeFunction response: {}", res);
                        return res.payload().asUtf8String();
                    })
                    .exceptionally(e -> {
                        LOG.error("Error invoking lambda function: {}", functionName, e);
                        return null;
                    });
            return futureResponse.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("LambdaException", e);
        }
        return null;
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                LambdaProxyConfig.CONFIG_NAME,
                LambdaProxyMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LambdaProxyConfig.CONFIG_NAME),
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

    @Override
    public void getCachedConfigurations() {

    }

    private void lookupMetricsMiddleware() {
        // get the metrics middleware instance from the chain.
        Map<String, LambdaHandler> handlers = Handler.getHandlers();
        metricsMiddleware = (AbstractMetricsMiddleware) handlers.get(MetricsConfig.CONFIG_NAME);
        if(metricsMiddleware == null) {
            LOG.error("An instance of MetricsMiddleware is not configured in the handler.yml file.");
        }
    }

}
