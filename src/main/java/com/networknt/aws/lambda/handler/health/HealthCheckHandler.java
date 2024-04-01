package com.networknt.aws.lambda.handler.health;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.health.HealthConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class HealthCheckHandler implements LambdaHandler {
    public static final String HEALTH_RESULT_OK = "OK";
    public static final String HEALTH_RESULT_ERROR = "ERROR";

    static final Logger logger = LoggerFactory.getLogger(HealthCheckHandler.class);
    static HealthConfig config;

    public HealthCheckHandler() {
        logger.info("HealthCheckHandler is constructed");
        config = HealthConfig.load();
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param cfg HealthConfig
     */
    public HealthCheckHandler(HealthConfig cfg) {
        config = cfg;
        logger.info("HealthCheckHandler is constructed");
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) throws InterruptedException {
        if(logger.isTraceEnabled()) logger.trace("HealthCheckHandler.handleRequest starts.");

        String result = HEALTH_RESULT_OK;

        if(config.isDownstreamEnabled()) {
            result = backendHealth();
        }

        // for security reason, we don't output the details about the error. Users can check the log for the failure.
        var responseEvent = new APIGatewayProxyResponseEvent();
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "text/plain");
        responseEvent.setHeaders(headers);
        if(HEALTH_RESULT_ERROR.equals(result)) {
            responseEvent.setStatusCode(400);
            responseEvent.setBody(HEALTH_RESULT_ERROR);
            if(logger.isTraceEnabled()) logger.trace("HealthCheckHandler.handleRequest ends with an error.");
        } else {
            responseEvent.setStatusCode(200);
            responseEvent.setBody(HEALTH_RESULT_OK);
            if(logger.isTraceEnabled()) logger.trace("HealthCheckHandler.handleRequest ends.");
        }
        exchange.setResponse(responseEvent);
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
        String result = HEALTH_RESULT_OK;
        long start = System.currentTimeMillis();
        // TODO call the backend health check endpoint

        long responseTime = System.currentTimeMillis() - start;
        if(logger.isDebugEnabled()) logger.debug("Downstream health check response time = " + responseTime);
        return result;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                HealthConfig.CONFIG_NAME,
                HealthCheckHandler.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(HealthConfig.CONFIG_NAME),
                null);
    }

    @Override
    public void reload() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }
}
