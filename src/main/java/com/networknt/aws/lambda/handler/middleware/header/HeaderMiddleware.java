package com.networknt.aws.lambda.handler.middleware.header;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.config.Config;
import com.networknt.header.HeaderConfig;
import com.networknt.oas.model.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public abstract class HeaderMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(HeaderMiddleware.class);
    protected String configName = HeaderConfig.CONFIG_NAME;

    public HeaderMiddleware() {
        LOG.info("HeaderMiddleware is constructed");
    }

    public HeaderMiddleware(String configName) {
        this.configName = configName;
        LOG.info("HeaderMiddleware is constructed with config {}", configName);
    }

    @Override
    public boolean isEnabled() {
        return HeaderConfig.load(configName).isEnabled();
    }

    public void removeHeaders(List<String> removeList, Map<String, String> headers) {
        // convert the list to lower case.
        Set<String> lowercaseSet = new HashSet<>();
        for(String s: removeList) {
            lowercaseSet.add(s.toLowerCase());
        }
        // remove headers with keys in removeList
        headers.entrySet().removeIf(entry -> lowercaseSet.contains(entry.getKey().toLowerCase()));
    }

    public void updateHeaders(Map<String, String> updateMap, Map<String, String> headers) {
        // convert update map key to lowercase
        Map<String, Object> lowercaseUpdateMap = new HashMap<>();
        for (Map.Entry<String, String> entry : updateMap.entrySet()) {
            lowercaseUpdateMap.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        // Iterate over the original header map and update values where keys match case-insensitively
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String lowercaseKey = entry.getKey().toLowerCase();
            if (lowercaseUpdateMap.containsKey(lowercaseKey)) {
                headers.put(entry.getKey(), (String)lowercaseUpdateMap.get(lowercaseKey));
            }
        }
        // convert the headers map to lowercase
        Map<String, String> lowercaseHeaders = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            lowercaseHeaders.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        // Iterate over the update map and add new entries to the header map
        for (Map.Entry<String, String> entry : updateMap.entrySet()) {
            String lowercaseKey = entry.getKey().toLowerCase();
            if (!lowercaseHeaders.containsKey(lowercaseKey)) {
                headers.put(entry.getKey(), (String)entry.getValue());
            }
        }
    }
}
