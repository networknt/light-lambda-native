package com.networknt.aws.lambda.middleware.proxy;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.proxy.LambdaProxyConfig;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import com.networknt.utility.PathTemplateMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.lambda.LambdaClient;

import java.util.HashMap;
import java.util.Map;

/**
 * This is a mock class for LambdaFunctionInvoker, and it is not calling the real Lambda function.
 * It is used in the unit test and should not be used in any live environment.
 *
 */
public class MockLambdaProxyMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MockLambdaProxyMiddleware.class);
    public static final String EXCHANGE_HAS_FAILED_STATE = "ERR10087";

    public static final LambdaProxyConfig CONFIG = (LambdaProxyConfig) Config.getInstance().getJsonObjectConfig(LambdaProxyConfig.CONFIG_NAME, LambdaProxyConfig.class);
    public static final Map<String, PathTemplateMatcher<String>> methodToMatcherMap = new HashMap<>();

    public MockLambdaProxyMiddleware() {
        populateMethodToMatcherMap(CONFIG.getFunctions());
        if (LOG.isInfoEnabled()) LOG.info("MockLambdaFunctionInvoker is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("MockLambdaFunctionInvoker.execute starts.");
        if (!exchange.hasFailedState()) {

            LOG.debug("Invoke Time - Start: {}", System.currentTimeMillis());
            /* invoke lambda function */
            var path = exchange.getRequest().getPath();
            var method = exchange.getRequest().getHttpMethod().toLowerCase();
            LOG.debug("Request path: {} -- Request method: {}", path, method);
            var functionName = CONFIG.getFunctions().get(path + "@" + method);
            var res = this.invokeFunction(null, functionName, exchange);
            LOG.debug("Invoke Time - Finish: {}", System.currentTimeMillis());
            var responseEvent = JsonMapper.fromJson(res, APIGatewayProxyResponseEvent.class);
            exchange.setInitialResponse(responseEvent);
            if(LOG.isTraceEnabled()) LOG.trace("LambdaFunctionInvoker.execute ends.");
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

    private String invokeFunction(final LambdaClient client, String functionName, final LightLambdaExchange exchange) {
        String serializedEvent = JsonMapper.toJson(exchange.getFinalizedRequest(false));
        if(LOG.isDebugEnabled()) LOG.debug("Serialized request event: {}", serializedEvent);
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Key3", "oldValue3");
        headers.put("key4", "oldValue4");
        headers.put("KeyC", "oldValueC");
        headers.put("keyD", "oldValueD");
        headers.put("header3", "value3");
        headers.put("Header4", "value4");
        headers.put("headerC", "valueC");
        headers.put("HeaderD", "valueD");
        responseEvent.setHeaders(headers);
        responseEvent.setStatusCode(200);
        responseEvent.setBody("{\"id\":1,\"name\":\"doggy\"}");
        if(LOG.isDebugEnabled()) LOG.debug("Serialized response event: {}", JsonMapper.toJson(responseEvent));
        return JsonMapper.toJson(responseEvent);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                LambdaProxyConfig.CONFIG_NAME,
                MockLambdaProxyMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LambdaProxyConfig.CONFIG_NAME),
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
}
