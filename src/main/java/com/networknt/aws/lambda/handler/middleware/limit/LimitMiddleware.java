package com.networknt.aws.lambda.handler.middleware.limit;


import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.HeaderValue;
import com.networknt.config.Config;
import com.networknt.limit.LimitConfig;
import com.networknt.limit.RateLimitResponse;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.ModuleRegistry;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class LimitMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LimitMiddleware.class);
    public static final String RATE_LIMIT_EXCEEDED = "ERR10088";
    private static LimitConfig CONFIG = LimitConfig.load();
    private static RateLimiter rateLimiter;
    private static final ObjectMapper mapper = Config.getInstance().getMapper();

    public LimitMiddleware() {
        if (LOG.isInfoEnabled()) LOG.info("LimitMiddleware is constructed");
        CONFIG = LimitConfig.load();
        try {
            rateLimiter = new RateLimiter(CONFIG);
        } catch (Exception e) {
            LOG.error("Exception:", e);
        }
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param cfg LimitConfig
     */
    public LimitMiddleware(LimitConfig cfg) {
        if (LOG.isInfoEnabled()) LOG.info("LimitMiddleware is constructed for unit tests");
        CONFIG = cfg;
        try {
            rateLimiter = new RateLimiter(CONFIG);
        } catch (Exception e) {
            LOG.error("Exception:", e);
        }
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) throws InterruptedException {
        if(LOG.isDebugEnabled()) LOG.debug("LimitMiddleware.execute starts.");
        RateLimitResponse rateLimitResponse = rateLimiter.handleRequest(exchange, CONFIG.getKey());
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
            int statusCode = CONFIG.getErrorCode()==0 ? 429:CONFIG.getErrorCode();
            responseEvent.setStatusCode(statusCode);
            responseEvent.setBody(status.toString());
            exchange.setResponse(responseEvent);
            if(LOG.isDebugEnabled()) LOG.warn("LimitHandler.handleRequest ends with an error code {}", RATE_LIMIT_EXCEEDED);
            return status;
        }
    }

    @Override
    public void getCachedConfigurations() {
        throw new NotImplementedException();
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                LimitConfig.CONFIG_NAME,
                LimitMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(LimitConfig.CONFIG_NAME),
                null
        );
    }

    @Override
    public void reload() {
        throw new NotImplementedException();
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
