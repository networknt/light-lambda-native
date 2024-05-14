package com.networknt.aws.lambda.handler.middleware.router;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.metrics.AbstractMetricsMiddleware;
import com.networknt.aws.lambda.utility.MapUtil;
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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.LambdaException;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LambdaRouterMiddleware implements MiddlewareHandler {
    private static LambdaClient client;
    private static final Logger LOG = LoggerFactory.getLogger(LambdaRouterMiddleware.class);
    private static AbstractMetricsMiddleware metricsMiddleware;

    public static final String FAILED_TO_INVOKE_LAMBDA = "ERR10086";
    public static final String EXCHANGE_HAS_FAILED_STATE = "ERR10087";

    public static final LambdaRouterConfig CONFIG = (LambdaRouterConfig) Config.getInstance().getJsonObjectConfig(LambdaRouterConfig.CONFIG_NAME, LambdaRouterConfig.class);

    static final Map<String, PathTemplateMatcher<String>> methodToMatcherMap = new HashMap<>();

    public LambdaRouterMiddleware() {
        var builder = LambdaClient.builder().region(Region.of(CONFIG.getRegion()));

        if (!StringUtils.isEmpty(CONFIG.getEndpointOverride()))
            builder.endpointOverride(URI.create(CONFIG.getEndpointOverride()));
        if(CONFIG.isMetricsInjection()) lookupMetricsMiddleware();
        client = builder.build();
        populateMethodToMatcherMap(CONFIG.getFunctions());
        if (LOG.isInfoEnabled()) LOG.info("LambdaRouterMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("LambdaRouterMiddleware.execute starts.");
        // check if the Function-Name is in the header. If it is, we will continue. Otherwise, return immediately.
        Optional<String> functionNameOptional = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), "Function-Name");
        if(functionNameOptional.isEmpty()) {
            LOG.error("Function-Name is not in the header. Skip LambdaRouterMiddleware.");
            return this.successMiddlewareStatus();
        } else {
            String functionNameInHeader = functionNameOptional.get();
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
                if(LOG.isTraceEnabled()) LOG.trace("Function name: {}, Header Function Name: {}", functionName, functionNameInHeader);
                // need to make sure both function names are the same.
                if(!functionName.equals(functionNameInHeader)) {
                    LOG.error("Function-Name in the header is different from the one in the configuration. Skip LambdaRouterMiddleware.");
                    return new Status(FAILED_TO_INVOKE_LAMBDA, functionName);
                }
                var res = this.invokeFunction(client, functionName, exchange);
                if (res == null) {
                    LOG.error("Failed to invoke lambda function: {}", functionName);
                    return new Status(FAILED_TO_INVOKE_LAMBDA, functionName);
                }
                LOG.debug("Invoke Time - Finish: {}", System.currentTimeMillis());
                var responseEvent = JsonMapper.fromJson(res, APIGatewayProxyResponseEvent.class);
                exchange.setInitialResponse(responseEvent);
                if(LOG.isTraceEnabled()) LOG.trace("LambdaRouterMiddleware.execute ends.");
                // TODO Here we need to stop the chain execution and return to the caller immediately.
                // TODO How to bypass the rest of the middleware chain and return to the caller?
                return this.successMiddlewareStatus();
            } else {
                LOG.error("Exchange has failed state {}", exchange.getState());
                return new Status(EXCHANGE_HAS_FAILED_STATE, exchange.getState());
            }
        }
    }

    private void populateMethodToMatcherMap(Map<String, String> functions) {
        if(functions == null) return;
        for (var entry : functions.entrySet()) {
            var endpoint = entry.getKey();
            var path = endpoint.split("@")[0];
            var method = endpoint.split("@")[1];
            PathTemplateMatcher<String> matcher = methodToMatcherMap.computeIfAbsent(method, k -> new PathTemplateMatcher<>());
            if(matcher.get(path) == null) matcher.add(path, entry.getValue());
            methodToMatcherMap.put(method, matcher);
        }
    }

    private String invokeFunction(final LambdaClient client, String functionName, final LightLambdaExchange exchange) {
        String serializedEvent = JsonMapper.toJson(exchange.getFinalizedRequest(false));
        String response = null;
        try {
            var payload = SdkBytes.fromUtf8String(serializedEvent);
            var request = InvokeRequest.builder()
                    .functionName(functionName)
                    .logType(CONFIG.getLogType())
                    .payload(payload)
                    .build();
            long startTime = System.nanoTime();
            var res = client.invoke(request);
            if(CONFIG.isMetricsInjection()) {
                if(metricsMiddleware == null) lookupMetricsMiddleware();
                if(metricsMiddleware != null) {
                    if (LOG.isTraceEnabled()) LOG.trace("Inject metrics for {}", CONFIG.getMetricsName());
                    metricsMiddleware.injectMetrics(exchange, startTime, CONFIG.getMetricsName(), null);
                }
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("lambda call function error:{}", res.functionError());
                LOG.debug("lambda logger result:{}", res.logResult());
                LOG.debug("lambda call status:{}", res.statusCode());
            }

            response = res.payload().asUtf8String();
            if(LOG.isTraceEnabled()) LOG.trace("response: {}", response);
        } catch (LambdaException e) {
            LOG.error("LambdaException", e);
        }
        return response;
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                LambdaRouterConfig.CONFIG_NAME,
                LambdaRouterMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LambdaRouterConfig.CONFIG_NAME),
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
