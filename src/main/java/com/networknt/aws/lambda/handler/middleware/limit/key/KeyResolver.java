package com.networknt.aws.lambda.handler.middleware.limit.key;

import com.networknt.aws.lambda.LightLambdaExchange;

/**
 * When rate limit is used, we need to define a key to identify a unique client or a unique IP address.
 * The information can be from different places in the exchange, and we might need to combine several
 * strategies to get a unique key.
 *
 * @author Steve Hu
 */
public interface KeyResolver {
    /**
     * Resolve a unique key from the exchange
     * @param exchange lambda exchange
     * @return A string for the key
     */
    String resolve(LightLambdaExchange exchange);
}
