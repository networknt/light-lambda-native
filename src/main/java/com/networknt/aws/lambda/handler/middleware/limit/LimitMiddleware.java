package com.networknt.aws.lambda.handler.middleware.limit;


import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.HeaderValue;
import com.networknt.config.Config;
import com.networknt.limit.LimitConfig;
import com.networknt.limit.RateLimitResponse;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class LimitMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LimitMiddleware.class);
    public static final String RATE_LIMIT_EXCEEDED = "ERR10088";
    private volatile String configName = LimitConfig.CONFIG_NAME;
    private volatile LimitConfig config;
    private volatile RateLimiter rateLimiter;

    public LimitMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("LimitMiddleware is constructed");
        config = LimitConfig.load(configName);
        try {
            rateLimiter = new RateLimiter(config);
        } catch (Exception e) {
            LOG.error("Exception:", e);
        }
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param configName String
     */
    public LimitMiddleware(String configName) {
        if (LOG.isInfoEnabled()) LOG.info("LimitMiddleware is constructed for unit tests with configName {}", configName);
        config = LimitConfig.load(configName);
        try {
            rateLimiter = new RateLimiter(config);
        } catch (Exception e) {
            LOG.error("Exception:", e);
        }
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) {
        if(LOG.isDebugEnabled()) LOG.debug("LimitMiddleware.execute starts.");
        LimitConfig newConfig = LimitConfig.load(configName);
        if(config != newConfig) {
            synchronized (this) {
                if (config != newConfig) {
                    config = newConfig;
                    try {
                        rateLimiter = new RateLimiter(config);
                    } catch (Exception e) {
                        LOG.error("Exception:", e);
                    }
                }
            }
        }
        RateLimitResponse rateLimitResponse = rateLimiter.handleRequest(exchange, config.getKey());
        if (rateLimitResponse.isAllow()) {
            if(LOG.isDebugEnabled()) LOG.debug("LimitHandler.handleRequest ends.");
            return successMiddlewareStatus();
        } else {
            Status status = new Status(RATE_LIMIT_EXCEEDED);
            var responseEvent = new APIGatewayProxyResponseEvent();
            var headers = new HashMap<String, String>();
            headers.put(HeaderKey.CONTENT_TYPE, HeaderValue.APPLICATION_JSON);
            headers.put(Constants.RATELIMIT_LIMIT, rateLimitResponse.getHeaders().get(Constants.RATELIMIT_LIMIT));
            headers.put(Constants.RATELIMIT_REMAINING, rateLimitResponse.getHeaders().get(Constants.RATELIMIT_REMAINING));
            headers.put(Constants.RATELIMIT_RESET, rateLimitResponse.getHeaders().get(Constants.RATELIMIT_RESET));
            responseEvent.setHeaders(headers);
            int statusCode = config.getErrorCode()==0 ? 429:config.getErrorCode();
            responseEvent.setStatusCode(statusCode);
            responseEvent.setBody(status.toString());
            exchange.setInitialResponse(responseEvent);
            if(LOG.isDebugEnabled()) LOG.warn("LimitHandler.handleRequest ends with an error code {}", RATE_LIMIT_EXCEEDED);
            return status;
        }
    }

    @Override
    public void getCachedConfigurations() {

    }

    @Override
    public boolean isEnabled() {
        return LimitConfig.load(configName).isEnabled();
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
}
