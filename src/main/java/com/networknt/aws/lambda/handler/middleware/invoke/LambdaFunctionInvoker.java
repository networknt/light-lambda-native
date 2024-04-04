package com.networknt.aws.lambda.handler.middleware.invoke;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import com.networknt.utility.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.LambdaException;

import java.net.URI;

public class LambdaFunctionInvoker implements MiddlewareHandler {

    private static LambdaClient client;
    private static final Logger LOG = LoggerFactory.getLogger(LambdaFunctionInvoker.class);
    public static final String FAILED_TO_INVOKE_LAMBDA = "ERR10086";
    public static final String EXCHANGE_HAS_FAILED_STATE = "ERR10087";

    public static final LambdaInvokerConfig CONFIG = (LambdaInvokerConfig) Config.getInstance().getJsonObjectConfig(LambdaInvokerConfig.CONFIG_NAME, LambdaInvokerConfig.class);

    public LambdaFunctionInvoker() {
        if (LOG.isInfoEnabled()) LOG.info("LambdaFunctionInvoker is constructed");
        var builder = LambdaClient.builder().region(Region.of(CONFIG.getRegion()));

        if (!StringUtils.isEmpty(CONFIG.getEndpointOverride()))
            builder.endpointOverride(URI.create(CONFIG.getEndpointOverride()));

        client = builder.build();
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
        if(LOG.isTraceEnabled()) LOG.trace("LambdaFunctionInvoker.execute starts.");
        if (!exchange.hasFailedState()) {

            LOG.debug("Invoke Time - Start: {}", System.currentTimeMillis());
            /* invoke lambda function */
            var path = exchange.getRequest().getPath();
            var method = exchange.getRequest().getHttpMethod().toLowerCase();
            LOG.debug("Request path: {} -- Request method: {}", path, method);
            var functionName = CONFIG.getFunctions().get(path + "@" + method);
            var res = this.invokeFunction(client, functionName, exchange);
            if (res == null) {
                LOG.error("Failed to invoke lambda function: {}", functionName);
                return new Status(FAILED_TO_INVOKE_LAMBDA, functionName);
            }

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

    private String invokeFunction(final LambdaClient client, String functionName, final LightLambdaExchange exchange) {
        String serializedEvent = null;
        try {
            serializedEvent = Config.getInstance().getMapper().writeValueAsString(exchange.getFinalizedRequest());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        String response = null;

        try {
            var payload = SdkBytes.fromUtf8String(serializedEvent);
            var request = InvokeRequest.builder()
                    .functionName(functionName)
                    .logType(CONFIG.getLogType())
                    .payload(payload)
                    .build();
            var res = client.invoke(request);

            if (LOG.isDebugEnabled()) {
                LOG.debug("lambda call function error:" + res.functionError());
                LOG.debug("lambda logger result:" + res.logResult());
                LOG.debug("lambda call status:" + res.statusCode());
            }

            response = res.payload().asUtf8String();
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
                LambdaInvokerConfig.CONFIG_NAME,
                LambdaFunctionInvoker.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LambdaInvokerConfig.CONFIG_NAME),
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


}
