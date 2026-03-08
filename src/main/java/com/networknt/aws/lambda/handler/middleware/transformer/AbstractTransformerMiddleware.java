package com.networknt.aws.lambda.handler.middleware.transformer;

import com.networknt.aws.lambda.handler.MiddlewareHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractTransformerMiddleware implements MiddlewareHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractTransformerMiddleware.class);

    public AbstractTransformerMiddleware() {
        LOG.info("AbstractTransformerMiddleware is constructed");
    }

    public static Map<String, String> convertMapValueToString(Map<String, Object> originalMap) {
        Map<String, String> convertedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : originalMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().toString(); // Convert Object to String
            convertedMap.put(key, value);
        }
        return convertedMap;
    }

}
