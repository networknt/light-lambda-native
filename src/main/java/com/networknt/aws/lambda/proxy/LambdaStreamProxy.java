package com.networknt.aws.lambda.proxy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * This is the entry point for the stream lambda function that is responsible for cross-cutting concerns for the business Lambda
 * function which is called from the is Lambda function once all cross-cutting concerns are addressed. The lambda function endpoint
 * receives the InputStream and the OutputStream.
 *
 * @author Steve Hu
 */
public class LambdaStreamProxy implements RequestStreamHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LambdaStreamProxy.class);
    private static final String CONFIG_NAME = "lambda-proxy";
    public static final LambdaProxyConfig CONFIG = (LambdaProxyConfig) Config.getInstance().getJsonObjectConfig(CONFIG_NAME, LambdaProxyConfig.class);

    public LambdaStreamProxy() {
        if (LOG.isInfoEnabled()) LOG.info("LambdaStreamProxy is constructed");
        Handler.init();
        ModuleRegistry.registerModule(
                LambdaProxyConfig.CONFIG_NAME,
                LambdaStreamProxy.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LambdaProxyConfig.CONFIG_NAME),
                null
        );
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        if(LOG.isDebugEnabled()) LOG.debug("Lambda CCC --start with request: {}", text);
        // try to convert the input text into APIGatewayProxyRequestEvent
        try {
            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = Config.getInstance().getMapper().readValue(text, APIGatewayProxyRequestEvent.class);
            var requestPath = apiGatewayProxyRequestEvent.getPath();
            var requestMethod = apiGatewayProxyRequestEvent.getHttpMethod();
            LOG.debug("Request path: {} -- Request method: {}", requestPath, requestMethod);
            Chain chain = Handler.getChain(requestPath + "@" + requestMethod);
            if(chain == null) chain = Handler.getDefaultChain();
            final var exchange = new LightLambdaExchange(context, chain);
            exchange.setRequest(apiGatewayProxyRequestEvent);
            exchange.executeChain();
            APIGatewayProxyResponseEvent response = exchange.getResponse();
            LOG.debug("Lambda CCC --end with response: {}", response);
            return;

        } catch (Exception e) {
            if(LOG.isDebugEnabled()) LOG.debug("Exception:", e);
            // this is a real stream body, and we need to use the default chain.
            Chain chain = Handler.getDefaultChain();
            final var exchange = new LightLambdaExchange(context, chain);

//            exchange.setRequest(apiGatewayProxyRequestEvent);
//            exchange.executeChain();
//            APIGatewayProxyResponseEvent response = exchange.getResponse();
//            LOG.debug("Lambda CCC --end with response: {}", response);
//            return response;

        }
    }

}
