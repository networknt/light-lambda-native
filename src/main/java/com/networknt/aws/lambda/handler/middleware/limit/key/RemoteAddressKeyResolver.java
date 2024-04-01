package com.networknt.aws.lambda.handler.middleware.limit.key;

import com.networknt.aws.lambda.handler.middleware.LightLambdaExchange;

import java.net.InetSocketAddress;

/**
 * When address is used as the key, we can get the IP address from the header of the request. If there
 * is no proxy before our service and gateway, we can use the remote address for the purpose.
 *
 * @author Steve Hu
 */
public class RemoteAddressKeyResolver implements KeyResolver {

    @Override
    public String resolve(LightLambdaExchange exchange) {
        return exchange.getRequest().getRequestContext().getIdentity().getSourceIp();
    }
}
