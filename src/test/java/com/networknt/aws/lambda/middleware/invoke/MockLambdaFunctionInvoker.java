package com.networknt.aws.lambda.middleware.invoke;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.middleware.invoke.LambdaFunctionInvoker;
import com.networknt.aws.lambda.handler.middleware.invoke.LambdaInvokerConfig;
import com.networknt.config.Config;
import com.networknt.config.JsonMapper;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.lambda.LambdaClient;

/**
 * This is a mock class for LambdaFunctionInvoker, and it is not calling the real Lambda function.
 * It is used in the unit test and should not be used in any live environment.
 *
 */
public class MockLambdaFunctionInvoker implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LambdaFunctionInvoker.class);
    public static final String EXCHANGE_HAS_FAILED_STATE = "ERR10087";

    public static final LambdaInvokerConfig CONFIG = (LambdaInvokerConfig) Config.getInstance().getJsonObjectConfig(LambdaInvokerConfig.CONFIG_NAME, LambdaInvokerConfig.class);

    public MockLambdaFunctionInvoker() {
        if (LOG.isInfoEnabled()) LOG.info("MockLambdaFunctionInvoker is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) throws InterruptedException {
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

    private String invokeFunction(final LambdaClient client, String functionName, final LightLambdaExchange exchange) {
        String serializedEvent = JsonMapper.toJson(exchange.getFinalizedRequest());
        if(LOG.isDebugEnabled()) LOG.debug("Serialized request event: {}", serializedEvent);
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
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
                LambdaInvokerConfig.CONFIG_NAME,
                MockLambdaFunctionInvoker.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LambdaInvokerConfig.CONFIG_NAME),
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
