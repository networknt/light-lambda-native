package com.networknt.aws.lambda.app;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.Handler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.chain.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


/**
 * This is the entry point for the middleware Lambda function that is responsible for cross-cutting concerns for the business Lambda
 * function which is called from the is Lambda function once all cross-cutting concerns are addressed. The middleware Lambda function
 * receives the APIGatewayProxyRequestEvent from the API Gateway and returns the APIGatewayProxyResponseEvent to the API Gateway.
 *
 * @author Steve Hu
 */
public class LambdaApp implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(LambdaApp.class);

    public static final LightLambdaExchange.Attachable<String> APP_ID = LightLambdaExchange.Attachable.createAttachable(String.class);
    private final LambdaAppConfig config;
    public LambdaApp() {
        this.config = LambdaAppConfig.load();
        Handler.init();
        LOG.info("LambdaApp is constructed");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent request, final Context context) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("file.encoding: {}", Charset.defaultCharset().displayName());
            LOG.debug("native.encoding: {}", System.getProperty("native.encoding"));
            LOG.debug("Lambda CCC --start with request: {}", request);
        }

        var requestPath = request.getPath();
        var requestMethod = request.getHttpMethod();

        if (shouldBase64EncodeRequest(request)) {
            byte[] bodyBytes = request.getBody().getBytes(StandardCharsets.UTF_8);
            request.setBody(Base64.getEncoder().encodeToString(bodyBytes));
            request.setIsBase64Encoded(true);
        }

        LOG.debug("Request path: {} -- Request method: {}", requestPath, requestMethod);

        Chain chain = Handler.getChain(request);
        if (chain == null)
            chain = Handler.getDefaultChain();

        final var exchange = new LightLambdaExchange(context, chain);
        exchange.addAttachment(APP_ID, config.getLambdaAppId());

        exchange.setInitialRequest(request);
        exchange.executeChain();

        APIGatewayProxyResponseEvent response = exchange.getFinalizedResponse(false);

        if (shouldBase64EncodeResponse(response)) {
            byte[] bodyBytes = response.getBody().getBytes(StandardCharsets.UTF_8);
            response.setBody(Base64.getEncoder().encodeToString(bodyBytes));
            response.setIsBase64Encoded(true);
        }

        LOG.debug("Lambda CCC --end with response: {}", response);
        return response;
    }

    private boolean shouldBase64EncodeResponse(final APIGatewayProxyResponseEvent response) {
        return config.isEncodeBase64Response() && response.getBody() != null && Boolean.FALSE.equals(response.getIsBase64Encoded());
    }

    private boolean shouldBase64EncodeRequest(final APIGatewayProxyRequestEvent request) {
        return config.isEncodeBase64Request() && request.getBody() != null && Boolean.FALSE.equals(request.getIsBase64Encoded());
    }
}
