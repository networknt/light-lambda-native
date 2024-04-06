package com.networknt.aws.lambda.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.config.Config;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This is the entry point for the middleware Lambda function that is responsible for cross-cutting concerns for the business Lambda
 * function which is called from the is Lambda function once all cross-cutting concerns are addressed. The middleware Lambda function
 * receives the APIGatewayProxyRequestEvent from the API Gateway and returns the APIGatewayProxyResponseEvent to the API Gateway.
 *
 * @author Steve Hu
 */
public class LambdaProxy implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(LambdaProxy.class);
    public static final LambdaProxyConfig CONFIG = (LambdaProxyConfig) Config.getInstance().getJsonObjectConfig(LambdaProxyConfig.CONFIG_NAME, LambdaProxyConfig.class);

    public LambdaProxy() {
        if (LOG.isInfoEnabled()) LOG.info("LambdaProxy is constructed");
        Handler.init();
        ModuleRegistry.registerModule(
                LambdaProxyConfig.CONFIG_NAME,
                LambdaProxy.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LambdaProxyConfig.CONFIG_NAME),
                null
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, final Context context) {
        LOG.debug("Lambda CCC --start with request: {}", apiGatewayProxyRequestEvent);
        var requestPath = apiGatewayProxyRequestEvent.getPath();
        var requestMethod = apiGatewayProxyRequestEvent.getHttpMethod();
        LOG.debug("Request path: {} -- Request method: {}", requestPath, requestMethod);
        Chain chain = Handler.getChain(requestPath + "@" + requestMethod.toLowerCase());
        if(chain == null) chain = Handler.getDefaultChain();
        final var exchange = new LightLambdaExchange(context, chain);
        exchange.setInitialRequest(apiGatewayProxyRequestEvent);
        exchange.executeChain();
        APIGatewayProxyResponseEvent response = exchange.getFinalizedResponse(false);
        LOG.debug("Lambda CCC --end with response: {}", response);
        return response;
    }

}
