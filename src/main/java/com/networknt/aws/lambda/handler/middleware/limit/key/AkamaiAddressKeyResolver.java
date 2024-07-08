package com.networknt.aws.lambda.handler.middleware.limit.key;

import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.utility.MapUtil;

import java.util.Map;
import java.util.Optional;

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
        Optional<String> valueString = MapUtil.getValueIgnoreCase(headerMap, "True-Client-IP");
        if(valueString.isPresent()) key = valueString.get();
        return key;
    }
}
