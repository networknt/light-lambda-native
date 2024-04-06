package com.networknt.aws.lambda.handler.middleware.header;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.config.Config;
import com.networknt.header.HeaderConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class HeaderMiddleware implements MiddlewareHandler {

    private static final String UNKNOWN_HEADER_OPERATION = "ERR14004";
    private static HeaderConfig CONFIG;
    private static final Logger LOG = LoggerFactory.getLogger(HeaderMiddleware.class);

    public HeaderMiddleware() {
        CONFIG = HeaderConfig.load();
        LOG.info("HeaderMiddleware is constructed");
    }

    /**
     * Constructor with configuration for testing purpose only
     * @param cfg HeaderConfig
     */
    public HeaderMiddleware(HeaderConfig cfg) {
        CONFIG = cfg;
        LOG.info("HeaderMiddleware is constructed");
    }

    @Override
    public Status execute(final LightLambdaExchange exchange) {
        if(LOG.isTraceEnabled()) LOG.trace("HeaderMiddleware.executeMiddleware starts.");
        if (!CONFIG.isEnabled()) {
            if(LOG.isTraceEnabled()) LOG.trace("HeaderMiddleware is not enabled.");
            return disabledMiddlewareStatus();
        }
        if(LOG.isTraceEnabled()) LOG.trace("HeaderMiddleware.executeMiddleware ends.");
        return this.handleHeaders(exchange);
    }


    private Status handleHeaders(LightLambdaExchange exchange) {
        // handle request headers
        if(exchange.isRequestInProgress()) {
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
                        removeList.forEach(requestHeaders::remove);
                    }
                    Map<String, Object> updateMap = CONFIG.getRequestUpdateMap();
                    if(updateMap != null) {
                        if(LOG.isTraceEnabled()) LOG.trace("Request header updateMap found.");
                        updateMap.forEach((k, v) -> requestHeaders.put(k, (String) v));
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
                                        requestHeaderRemoveList.forEach(requestHeaders::remove);
                                    }
                                    Map<String, Object> requestHeaderUpdateMap = (Map<String, Object>)requestHeaderMap.get(HeaderConfig.UPDATE);
                                    if(requestHeaderUpdateMap != null) {
                                        if(LOG.isTraceEnabled()) LOG.trace("Request header path prefix updateMap found.");
                                        requestHeaderUpdateMap.forEach((k, v) -> requestHeaders.put(k, (String) v));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if(exchange.isResponseInProgress()) {
            // handle response headers
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
                        removeList.forEach(responseHeaders::remove);
                    }
                    Map<String, Object> updateMap = CONFIG.getResponseUpdateMap();
                    if(updateMap != null) {
                        if(LOG.isTraceEnabled()) LOG.trace("Response header updateMap found.");
                        updateMap.forEach((k, v) -> responseHeaders.put(k, (String) v));
                    }
                    // handle per path prefix header if configured
                    Map<String, Object> pathPrefixHeader = CONFIG.getPathPrefixHeader();
                    if(pathPrefixHeader != null) {
                        String path = exchange.getRequest().getPath();
                        for (Map.Entry<String, Object> entry : pathPrefixHeader.entrySet()) {
                            if(path.startsWith(entry.getKey())) {
                                if(LOG.isTraceEnabled()) LOG.trace("Found path " + path + " with prefix " + entry.getKey());
                                Map<String, Object> valueMap = (Map<String, Object>)entry.getValue();
                                Map<String, Object> responseHeaderMap = (Map<String, Object>)valueMap.get(HeaderConfig.RESPONSE);
                                if(responseHeaderMap != null) {
                                    List<String> responseHeaderRemoveList = (List<String>)responseHeaderMap.get(HeaderConfig.REMOVE);
                                    if(responseHeaderRemoveList != null) {
                                        if(LOG.isTraceEnabled()) LOG.trace("Response header path prefix removeList found.");
                                        responseHeaderRemoveList.forEach(responseHeaders::remove);
                                    }
                                    Map<String, Object> responseHeaderUpdateMap = (Map<String, Object>)responseHeaderMap.get(HeaderConfig.UPDATE);
                                    if(responseHeaderUpdateMap != null) {
                                        if(LOG.isTraceEnabled()) LOG.trace("Response header path prefix updateMap found.");
                                        responseHeaderUpdateMap.forEach((k, v) -> responseHeaders.put(k, (String) v));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return successMiddlewareStatus();
    }

    @Override
    public void getCachedConfigurations() {
    }

    @Override
    public boolean isEnabled() {
        return CONFIG.isEnabled();
    }

    @Override
    public void register() {
        ModuleRegistry.registerModule(
                HeaderConfig.CONFIG_NAME,
                HeaderMiddleware.class.getName(),
                Config.getNoneDecryptedInstance().getJsonMapConfigNoCache(HeaderConfig.CONFIG_NAME),
                null
        );
    }

    @Override
    public void reload() {

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
