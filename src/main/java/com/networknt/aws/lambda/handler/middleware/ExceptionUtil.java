package com.networknt.aws.lambda.handler.middleware;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.HeaderValue;
import com.networknt.status.Status;

import java.util.HashMap;
import java.util.List;

/**
 * This class is used to handle exceptions and return the response to the client. It is called by
 * the LightLambdaExchange when returning response from the exchange.
 *
 */
public class ExceptionUtil {

    private ExceptionUtil() {
        throw new IllegalStateException("ExceptionUtil is a utility class");
    }

    /**
     * Convert the middleware results to APIGatewayProxyResponseEvent.
     * The first error result from the result list gets converted into an error returned to the client.
     *
     * @param middlewareResults A list of middleware results.
     * @return APIGatewayProxyResponseEvent containing status information.
     */
    public static APIGatewayProxyResponseEvent convert(final List<Status> middlewareResults) {
        var responseEvent = new APIGatewayProxyResponseEvent();
        var headers = new HashMap<String, String>();

        headers.put(HeaderKey.CONTENT_TYPE, HeaderValue.APPLICATION_JSON);
        responseEvent.setHeaders(headers);

        for (var res : middlewareResults)
            if (res != null && res.getCode().startsWith("ERR")) {
                responseEvent.setStatusCode(res.getStatusCode());
                responseEvent.setBody(res.toString());
                responseEvent.setIsBase64Encoded(false);
                return responseEvent;
            }
        return new APIGatewayProxyResponseEvent();
    }
}
