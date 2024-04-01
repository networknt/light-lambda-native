package com.networknt.aws.lambda.handler.middleware.limit.key;

import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;

import java.util.Map;

/**
 * When native-lambda is used for external clients and all external requests go through the
 * Akamai cloud proxy, the real client IP can be retrieved from the header as True-Client-IP
 *
 * @author Steve Hu
 */
public class AkamaiAddressKeyResolver implements KeyResolver {

    @Override
    public String resolve(LightLambdaExchange exchange) {
        String key = "127.0.0.1";
        Map<String, String> headerMap = exchange.getResponse().getHeaders();
        String value = headerMap.get("True-Client-IP");
        if(value != null) key = value;
        return key;
    }
}
