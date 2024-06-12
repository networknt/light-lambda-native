package com.networknt.aws.lambda.handler.middleware.header;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.header.HeaderConfig;
import com.networknt.status.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResponseHeaderMiddleware extends HeaderMiddleware implements MiddlewareHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ResponseHeaderMiddleware.class);

    public ResponseHeaderMiddleware() {
        super();
        LOG.info("ResponseHeaderMiddleware is constructed");
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param cfg HeaderConfig
     */
    public ResponseHeaderMiddleware(HeaderConfig cfg) {
        super(cfg);
        LOG.info("ResponseHeaderMiddleware is constructed");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("ResponseHeaderMiddleware.executeMiddleware starts.");
        if (!CONFIG.isEnabled()) {
            if(LOG.isTraceEnabled()) LOG.trace("ResponseHeaderMiddleware is not enabled.");
            return disabledMiddlewareStatus();
        }
        APIGatewayProxyResponseEvent responseEvent = exchange.getResponse();
        if(responseEvent != null) {
            if (LOG.isTraceEnabled()) LOG.trace("Response event is not null.");
            var responseHeaders = responseEvent.getHeaders();
            if (responseHeaders != null) {
                if (LOG.isTraceEnabled()) LOG.trace("Response headers is not null.");
                // handler all response header
                List<String> removeList = CONFIG.getResponseRemoveList();
                if(removeList != null) {
                    if(LOG.isTraceEnabled()) LOG.trace("Response header removeList found.");
                    removeHeaders(removeList, responseHeaders);
                }
                Map<String, Object> updateMap = CONFIG.getResponseUpdateMap();
                if(updateMap != null) {
                    if(LOG.isTraceEnabled()) LOG.trace("Response header updateMap found.");
                    updateHeaders(updateMap, responseHeaders);
                }
                // handle per path prefix header if configured
                Map<String, Object> pathPrefixHeader = CONFIG.getPathPrefixHeader();
                if(pathPrefixHeader != null) {
                    String path = exchange.getReadOnlyRequest().getPath();
                    for (Map.Entry<String, Object> entry : pathPrefixHeader.entrySet()) {
                        if(path.startsWith(entry.getKey())) {
                            if(LOG.isTraceEnabled()) LOG.trace("Found path {} with prefix {}", path, entry.getKey());
                            Map<String, Object> valueMap = (Map<String, Object>)entry.getValue();
                            Map<String, Object> responseHeaderMap = (Map<String, Object>)valueMap.get(HeaderConfig.RESPONSE);
                            if(responseHeaderMap != null) {
                                List<String> responseHeaderRemoveList = (List<String>)responseHeaderMap.get(HeaderConfig.REMOVE);
                                if(responseHeaderRemoveList != null) {
                                    if(LOG.isTraceEnabled()) LOG.trace("Response header path prefix removeList found.");
                                    removeHeaders(responseHeaderRemoveList, responseHeaders);
                                }
                                Map<String, Object> responseHeaderUpdateMap = (Map<String, Object>)responseHeaderMap.get(HeaderConfig.UPDATE);
                                if(responseHeaderUpdateMap != null) {
                                    if(LOG.isTraceEnabled()) LOG.trace("Response header path prefix updateMap found.");
                                    updateHeaders(responseHeaderUpdateMap, responseHeaders);
                                }
                            }
                        }
                    }
                }
            }
        }
        if(LOG.isTraceEnabled()) LOG.trace("ResponseHeaderMiddleware.executeMiddleware ends.");
        return successMiddlewareStatus();
    }

    @Override
    public boolean isResponseMiddleware() {
        return true;
    }
}
