package com.networknt.aws.lambda.handler.middleware.header;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.config.Config;
import com.networknt.header.HeaderConfig;
import com.networknt.status.Status;
import com.networknt.utility.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public abstract class HeaderMiddleware implements MiddlewareHandler {
    static HeaderConfig CONFIG;
    private static final Logger LOG = LoggerFactory.getLogger(HeaderMiddleware.class);

    public HeaderMiddleware() {
        CONFIG = HeaderConfig.load();
    }

    public HeaderMiddleware(HeaderConfig cfg) {
        CONFIG = cfg;
        LOG.info("HeaderMiddleware is constructed");
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
    public boolean isAsynchronous() {
        return false;
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
    public void getCachedConfigurations() {

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

    public void updateHeaders(Map<String, Object> updateMap, Map<String, String> headers) {
        // convert update map key to lowercase
        Map<String, Object> lowercaseUpdateMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : updateMap.entrySet()) {
            lowercaseUpdateMap.put(entry.getKey().toLowerCase(), entry.getValue());
        }
        // Iterate over the original map and update values where keys match case-insensitively
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String lowercaseKey = entry.getKey().toLowerCase();
            if (lowercaseUpdateMap.containsKey(lowercaseKey)) {
                headers.put(entry.getKey(), (String)lowercaseUpdateMap.get(lowercaseKey));
            }
        }

    }
}
