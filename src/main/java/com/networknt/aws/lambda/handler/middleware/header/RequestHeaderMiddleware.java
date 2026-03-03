package com.networknt.aws.lambda.handler.middleware.header;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.header.HeaderConfig;
import com.networknt.header.HeaderPathPrefixConfig;
import com.networknt.header.HeaderRequestConfig;
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
     *
     * @param configName String
     */
    public RequestHeaderMiddleware(String configName) {
        super(configName);
        LOG.info("RequestHeaderMiddleware is constructed with config {}", configName);
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) {
        LOG.trace("RequestHeaderMiddleware.executeMiddleware starts.");
        APIGatewayProxyRequestEvent requestEvent = exchange.getRequest();
        if (requestEvent != null) {
            LOG.trace("Request event is not null.");
            var requestHeaders = requestEvent.getHeaders();
            if (requestHeaders != null) {
                LOG.trace("Request headers is not null.");
                // handle all request header
                List<String> removeList = config.getRequestRemoveList();
                if (removeList != null) {
                    LOG.trace("Request header removeList found.");
                    removeHeaders(removeList, requestHeaders);
                }
                Map<String, String> updateMap = config.getRequestUpdateMap();
                if (updateMap != null) {
                    LOG.trace("Request header updateMap found.");
                    updateHeaders(updateMap, requestHeaders);
                }

                // handle per path prefix header if configured
                Map<String, HeaderPathPrefixConfig> pathPrefixHeader = config.getPathPrefixHeader();
                if (pathPrefixHeader != null) {
                    String path = exchange.getRequest().getPath();
                    for (Map.Entry<String, HeaderPathPrefixConfig> entry : pathPrefixHeader.entrySet()) {
                        if (path.startsWith(entry.getKey())) {
                            LOG.trace("Found path {} with prefix {}", path, entry.getKey());
                            HeaderPathPrefixConfig headerPathPrefixConfig = entry.getValue();
                            HeaderRequestConfig headerRequestConfig = headerPathPrefixConfig.getRequest();
                            if (headerRequestConfig != null) {
                                List<String> requestHeaderRemoveList = headerRequestConfig.getRemove();
                                if (requestHeaderRemoveList != null) {
                                    LOG.trace("Request header path prefix removeList found.");
                                    removeHeaders(requestHeaderRemoveList, requestHeaders);
                                }
                                Map<String, String> requestHeaderUpdateMap = headerRequestConfig.getUpdate();
                                if (requestHeaderUpdateMap != null) {
                                    LOG.trace("Request header path prefix updateMap found.");
                                    updateHeaders(requestHeaderUpdateMap, requestHeaders);
                                }
                            }
                        }
                    }
                }
            }
        }
        LOG.trace("RequestHeaderMiddleware.executeMiddleware ends.");
        return successMiddlewareStatus();
    }
}
