package com.networknt.aws.lambda.handler.middleware.header;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.header.HeaderConfig;
import com.networknt.header.HeaderPathPrefixConfig;
import com.networknt.header.HeaderRequestConfig;
import com.networknt.header.HeaderResponseConfig;
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
     * @param configName String
     */
    public ResponseHeaderMiddleware(String configName) {
        super(configName);
        LOG.info("ResponseHeaderMiddleware is constructed with config {}", configName);
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("ResponseHeaderMiddleware.executeMiddleware starts.");
        HeaderConfig config = HeaderConfig.load(configName);
        if (!config.isEnabled()) {
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
                List<String> removeList = config.getResponseRemoveList();
                if(removeList != null) {
                    if(LOG.isTraceEnabled()) LOG.trace("Response header removeList found.");
                    removeHeaders(removeList, responseHeaders);
                }
                Map<String, String> updateMap = config.getResponseUpdateMap();
                if(updateMap != null) {
                    if(LOG.isTraceEnabled()) LOG.trace("Response header updateMap found.");
                    updateHeaders(updateMap, responseHeaders);
                }
                // handle per path prefix header if configured
                Map<String, HeaderPathPrefixConfig> pathPrefixHeader = config.getPathPrefixHeader();
                if(pathPrefixHeader != null) {
                    String path = exchange.getReadOnlyRequest().getPath();
                    for (Map.Entry<String, HeaderPathPrefixConfig> entry : pathPrefixHeader.entrySet()) {
                        if(path.startsWith(entry.getKey())) {
                            if(LOG.isTraceEnabled()) LOG.trace("Found path {} with prefix {}", path, entry.getKey());
                            HeaderPathPrefixConfig headerPathPrefixConfig = entry.getValue();
                            HeaderResponseConfig headerResponseConfig = headerPathPrefixConfig.getResponse();
                            if(headerResponseConfig != null) {
                                List<String> responseHeaderRemoveList = headerResponseConfig.getRemove();
                                if(responseHeaderRemoveList != null) {
                                    if(LOG.isTraceEnabled()) LOG.trace("Response header path prefix removeList found.");
                                    removeHeaders(responseHeaderRemoveList, responseHeaders);
                                }
                                Map<String, String> responseHeaderUpdateMap = headerResponseConfig.getUpdate();
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
}
