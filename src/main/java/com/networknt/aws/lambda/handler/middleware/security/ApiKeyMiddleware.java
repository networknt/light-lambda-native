package com.networknt.aws.lambda.handler.middleware.security;

import com.networknt.apikey.ApiKey;
import com.networknt.apikey.ApiKeyConfig;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.handler.MiddlewareHandler;
import com.networknt.utility.MapUtil;
import com.networknt.config.Config;
import com.networknt.status.Status;
import com.networknt.utility.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ApiKeyMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyMiddleware.class);
    static final String API_KEY_MISMATCH = "ERR10075";

    private volatile String configName = ApiKeyConfig.CONFIG_NAME;

    public ApiKeyMiddleware() {
        if(LOG.isTraceEnabled()) LOG.trace("ApiKeyMiddleware is loaded.");
    }
    /**
     * This is a constructor for test cases only. Please don't use it.
     * @param configName String
     */
    @Deprecated
    public ApiKeyMiddleware(String configName) {
        this.configName = configName;
        if(LOG.isInfoEnabled()) LOG.info("ApiKeyMiddleware is loaded.");
    }

    @Override
    public Status execute(LightLambdaExchange exchange) {
        if(LOG.isDebugEnabled()) LOG.debug("ApiKeyMiddleware.execute starts.");
        String requestPath = exchange.getRequest().getPath();
        return handleApiKey(exchange, requestPath);
    }

    public Status handleApiKey(LightLambdaExchange exchange, String requestPath) {
        if(LOG.isTraceEnabled()) LOG.trace("requestPath = {}", requestPath);
        ApiKeyConfig config = ApiKeyConfig.load(configName);
        if (config.getPathPrefixAuths() != null) {
            boolean matched = false;
            boolean found = false;
            // iterate all the ApiKey entries to find if any of them matches the request path.
            for(ApiKey apiKey: config.getPathPrefixAuths()) {
                if(requestPath.startsWith(apiKey.getPathPrefix())) {
                    found = true;
                    // found the matched prefix, validate the apiKey by getting the header and compare.
                    Optional<String> optionalKey = MapUtil.getValueIgnoreCase(exchange.getRequest().getHeaders(), apiKey.getHeaderName());
                    if(config.isHashEnabled()) {
                        // hash the apiKey and compare with the one in the config.
                        try {
                            matched = HashUtil.validatePassword(optionalKey.map(String::toCharArray).orElse(null), apiKey.getApiKey());
                            if(matched) {
                                if (LOG.isTraceEnabled()) LOG.trace("Found valid apiKey with prefix = " + apiKey.getPathPrefix() + " headerName = " + apiKey.getHeaderName());
                                break;
                            }
                        } catch (Exception e) {
                            // there is no way to get here as the validatePassword will not throw any exception.
                            LOG.error("Exception:", e);
                        }
                    } else {
                        // if not hash enabled, then compare the apiKey directly.
                        if(apiKey.getApiKey().equals(optionalKey.orElse(null))) {
                            if (LOG.isTraceEnabled()) LOG.trace("Found matched apiKey with prefix = " + apiKey.getPathPrefix() + " headerName = " + apiKey.getHeaderName());
                            matched = true;
                            break;
                        }
                    }
                }
            }
            if(!found) {
                // the request path is no in the configuration, consider pass and go to the next handler.
                return successMiddlewareStatus();
            }
            if(!matched) {
                // at this moment, if not matched, then return an error message.
                LOG.error("Could not find matched APIKEY for request path " + requestPath);
                if(LOG.isDebugEnabled()) LOG.debug("ApiKeyMiddleware.execute ends with an error.");
                return new Status(API_KEY_MISMATCH, requestPath);
            }
        }
        return successMiddlewareStatus();
    }

    @Override
    public boolean isEnabled() {
        return ApiKeyConfig.load(configName).isEnabled();
    }
}
