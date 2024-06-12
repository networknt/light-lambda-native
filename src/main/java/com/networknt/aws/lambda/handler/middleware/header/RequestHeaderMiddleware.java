package com.networknt.aws.lambda.handler.middleware.header;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.header.HeaderConfig;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class RequestHeaderMiddleware extends HeaderMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(RequestHeaderMiddleware.class);

    public RequestHeaderMiddleware() {
        super();
        LOG.info("RequestHeaderMiddleware is constructed");
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param cfg HeaderConfig
     */
    public RequestHeaderMiddleware(HeaderConfig cfg) {
        super(cfg);
        LOG.info("RequestHeaderMiddleware is constructed");
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("RequestHeaderMiddleware.executeMiddleware starts.");
        if (!CONFIG.isEnabled()) {
            if(LOG.isTraceEnabled()) LOG.trace("RequestHeaderMiddleware is not enabled.");
            return disabledMiddlewareStatus();
        }
        APIGatewayProxyRequestEvent requestEvent = exchange.getRequest();
        if(requestEvent != null) {
            if(LOG.isTraceEnabled()) LOG.trace("Request event is not null.");
            var requestHeaders = requestEvent.getHeaders();
            if(requestHeaders != null) {
                if(LOG.isTraceEnabled()) LOG.trace("Request headers is not null.");
                // handle all request header
                List<String> removeList = CONFIG.getRequestRemoveList();
                if (removeList != null) {
                    if(LOG.isTraceEnabled()) LOG.trace("Request header removeList found.");
                    removeHeaders(removeList, requestHeaders);
                }
                Map<String, Object> updateMap = CONFIG.getRequestUpdateMap();
                if(updateMap != null) {
                    if(LOG.isTraceEnabled()) LOG.trace("Request header updateMap found.");
                    updateHeaders(updateMap, requestHeaders);
                }

                // handle per path prefix header if configured
                Map<String, Object> pathPrefixHeader = CONFIG.getPathPrefixHeader();
                if(pathPrefixHeader != null) {
                    String path = exchange.getRequest().getPath();
                    for (Map.Entry<String, Object> entry : pathPrefixHeader.entrySet()) {
                        if(path.startsWith(entry.getKey())) {
                            if(LOG.isTraceEnabled()) LOG.trace("Found path " + path + " with prefix " + entry.getKey());
                            Map<String, Object> valueMap = (Map<String, Object>)entry.getValue();
                            Map<String, Object> requestHeaderMap = (Map<String, Object>)valueMap.get(HeaderConfig.REQUEST);
                            if(requestHeaderMap != null) {
                                List<String> requestHeaderRemoveList = (List<String>)requestHeaderMap.get(HeaderConfig.REMOVE);
                                if(requestHeaderRemoveList != null) {
                                    if(LOG.isTraceEnabled()) LOG.trace("Request header path prefix removeList found.");
                                    removeHeaders(requestHeaderRemoveList, requestHeaders);
                                }
                                Map<String, Object> requestHeaderUpdateMap = (Map<String, Object>)requestHeaderMap.get(HeaderConfig.UPDATE);
                                if(requestHeaderUpdateMap != null) {
                                    if(LOG.isTraceEnabled()) LOG.trace("Request header path prefix updateMap found.");
                                    updateHeaders(requestHeaderUpdateMap, requestHeaders);
                                }
                            }
                        }
                    }
                }
            }
        }
        if(LOG.isTraceEnabled()) LOG.trace("RequestHeaderMiddleware.executeMiddleware ends.");
        return successMiddlewareStatus();
    }
}
