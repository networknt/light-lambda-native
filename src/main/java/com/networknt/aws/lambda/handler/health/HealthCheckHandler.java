package com.networknt.aws.lambda.handler.health;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.health.HealthConfig;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class HealthCheckHandler implements LambdaHandler {
    public static final String HEALTH_RESULT_OK = "OK";
    public static final String HEALTH_RESULT_ERROR = "ERROR";

    static final Logger logger = LoggerFactory.getLogger(HealthCheckHandler.class);

    public HealthCheckHandler() {
        logger.info("HealthCheckHandler is constructed");
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) {
        if(logger.isTraceEnabled()) logger.trace("HealthCheckHandler.handleRequest starts.");
        HealthConfig config = HealthConfig.load();

        String result = HEALTH_RESULT_OK;

        if(config.isDownstreamEnabled()) {
            result = backendHealth();
        }

        // for security reason, we don't output the details about the error. Users can check the log for the failure.
        var responseEvent = new APIGatewayProxyResponseEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain; charset=utf-8");
        responseEvent.setHeaders(headers);
        responseEvent.setIsBase64Encoded(false);
        if(HEALTH_RESULT_ERROR.equals(result)) {
            responseEvent.setStatusCode(400);
            responseEvent.setBody(HEALTH_RESULT_ERROR);
            if(logger.isTraceEnabled()) logger.trace("HealthCheckHandler.handleRequest ends with an error.");
        } else {
            responseEvent.setStatusCode(200);
            responseEvent.setBody(HEALTH_RESULT_OK);
            if(logger.isTraceEnabled()) logger.trace("HealthCheckHandler.handleRequest ends.");
        }
        exchange.setInitialResponse(responseEvent);
        if(logger.isTraceEnabled()) logger.trace("HealthCheckHandler.handleRequest ends.");
        return this.successMiddlewareStatus();
    }

    /**
     * Try to access the configurable /health endpoint on the backend Lambda. return OK if a success response is returned.
     * Otherwise, ERROR is returned.
     *
     * @return result String of OK or ERROR.
     */
    private String backendHealth() {
        long start = System.currentTimeMillis();
        // TODO call the backend health check endpoint

        long responseTime = System.currentTimeMillis() - start;
        if(logger.isDebugEnabled()) logger.debug("Downstream health check response time = {}", responseTime);
        return HEALTH_RESULT_OK;
    }

    @Override
    public boolean isEnabled() {
        return HealthConfig.load().isEnabled();
    }
}
