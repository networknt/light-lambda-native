package com.networknt.aws.lambda.app;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.chain.Chain;
import com.networknt.utility.PathTemplateMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;


/**
 * This is the entry point for the middleware Lambda function that is responsible for cross-cutting concerns for the business Lambda
 * function which is called from the is Lambda function once all cross-cutting concerns are addressed. The middleware Lambda function
 * receives the APIGatewayProxyRequestEvent from the API Gateway and returns the APIGatewayProxyResponseEvent to the API Gateway.
 *
 * @author Steve Hu
 */
public class LambdaApp implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(LambdaApp.class);
    static final Map<String, PathTemplateMatcher<String>> methodToMatcherMap = new HashMap<>();

    public LambdaApp() {
        LOG.info("LambdaApp is constructed");
        // Call the config load to register it only. This appId change doesn't support config reload as it is
        // called only once here in the constructor.
        LambdaAppConfig.load();
        Handler.init();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent, final Context context) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("file.encoding: {}", Charset.defaultCharset().displayName());
            LOG.debug("sun.stdout.encoding: {}", System.getProperty("sun.stdout.encoding"));
            LOG.debug("sun.stderr.encoding: {}", System.getProperty("sun.stderr.encoding"));
            LOG.debug("native.encoding: {}", System.getProperty("native.encoding"));
        }

        LOG.debug("Lambda CCC --start with request: {}", apiGatewayProxyRequestEvent);
        var requestPath = apiGatewayProxyRequestEvent.getPath();
        var requestMethod = apiGatewayProxyRequestEvent.getHttpMethod();

        LOG.debug("Request path: {} -- Request method: {}", requestPath, requestMethod);

        Chain chain = Handler.getChain(apiGatewayProxyRequestEvent);
        if (chain == null)
            chain = Handler.getDefaultChain();

        final var exchange = new LightLambdaExchange(context, chain);

        exchange.setInitialRequest(apiGatewayProxyRequestEvent);
        exchange.executeChain();

        APIGatewayProxyResponseEvent response = exchange.getFinalizedResponse(false);

        LOG.debug("Lambda CCC --end with response: {}", response);
        return response;
    }
}
