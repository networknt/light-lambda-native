package com.networknt.aws.lambda.handler.cache;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.LambdaHandler;
import com.networknt.cache.CacheManager;
import com.networknt.config.JsonMapper;
import com.networknt.service.SingletonServiceFactory;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class CacheExplorerHandler implements LambdaHandler {
    static final Logger logger = LoggerFactory.getLogger(CacheExplorerHandler.class);
    public static final String CACHE_NAME = "name";
    public static final String JWK = "jwk";
    public static final String OBJECT_NOT_FOUND = "ERR11637";

    public CacheExplorerHandler() {
        logger.info("CacheExplorerHandler is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if (logger.isTraceEnabled()) logger.trace("CacheExplorerHandler.execute starts.");
        Map<String, String> headers = Map.of("Content-Type", "application/json");
        String name = exchange.getRequest().getPathParameters().get(CACHE_NAME);
        CacheManager cacheManager = SingletonServiceFactory.getBean(CacheManager.class);
        if(cacheManager != null) {
            Map<Object, Object> cacheMap = cacheManager.getCache(name);
            if(name.equals(JWK)) {
                Map<String, String> map = new HashMap<>();
                cacheMap.forEach((k, v) -> {
                    map.put((String)k, v.toString());
                });
                var res = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(headers)
                        .withBody(JsonMapper.toJson((map)));
                exchange.setInitialResponse(res);
            } else {
                var res = new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(headers)
                        .withBody(JsonMapper.toJson((cacheMap)));
                exchange.setInitialResponse(res);
            }
        } else {
            Status status = new Status(OBJECT_NOT_FOUND, "cache", name);
            var res = new APIGatewayProxyResponseEvent()
                    .withStatusCode(status.getStatusCode())
                    .withHeaders(headers)
                    .withBody(status.toString());
            exchange.setInitialResponse(res);
        }
        if (logger.isTraceEnabled()) logger.trace("CacheExplorerHandler.execute ends.");
        return this.successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isAsynchronous() {
        return false;
    }

}
