package com.networknt.aws.lambda.handler.middleware.cors;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.config.Config;
import com.networknt.cors.CorsConfig;
import com.networknt.status.Status;
import com.networknt.utility.MapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This middleware is responsible for adding CORS headers to the response if the request is a CORS request.
 * It means the request has a header Origin. The middleware will add the following headers to the response:
 * Access-Control-Allow-Origin: The value of the Origin header
 * Access-Control-Allow-Methods: The value of the Access-Control-Request-Method header
 *
 * @author Steve Hu
 */
public class ResponseCorsMiddleware implements MiddlewareHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ResponseCorsMiddleware.class);

    public ResponseCorsMiddleware() {
        LOG.info("ResponseCorsMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("RequestCorsMiddleware.executeMiddleware starts.");
        CorsConfig config = CorsConfig.load();
        if (!config.isEnabled()) {
            if(LOG.isTraceEnabled()) LOG.trace("RequestCorsMiddleware is not enabled.");
            return disabledMiddlewareStatus();
        }
        APIGatewayProxyResponseEvent responseEvent = exchange.getResponse();
        if(responseEvent != null) {
            if (LOG.isTraceEnabled()) LOG.trace("Response event is not null.");
            var responseHeaders = responseEvent.getHeaders();
            if (responseHeaders != null) {
                if (LOG.isTraceEnabled()) LOG.trace("Response headers is not null.");
                if (MapUtil.getValueIgnoreCase(exchange.getReadOnlyRequest().getHeaders(), "Origin").isPresent()) {
                    // this is a CORS request, and it is passed the CORS check in the RequestCorsMiddleware.
                    responseHeaders.put("Access-Control-Allow-Origin", exchange.getReadOnlyRequest().getHeaders().get("Origin"));
                }
            }
        }
        if(LOG.isTraceEnabled()) LOG.trace("RequestCorsMiddleware.executeMiddleware ends.");
        return successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return CorsConfig.load().isEnabled();
    }

}
