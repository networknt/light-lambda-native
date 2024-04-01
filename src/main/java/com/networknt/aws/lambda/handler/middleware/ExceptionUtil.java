package com.networknt.aws.lambda.handler.middleware;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.aws.lambda.utility.HeaderValue;
import com.networknt.status.Status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class is used to handle exceptions and return the response to the client. It is called by
 * the LightLambdaExchange when returning response from the exchange.
 *
 */
public class ExceptionUtil {

    private static final String DATA_KEY = "data";
    private static final String NOTIFICATIONS_KEY = "notifications";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Convert the middleware results to APIGatewayProxyResponseEvent
     *
     * @param middlewareResults a list of middleware results
     * @return APIGatewayProxyResponseEvent
     */
    public static APIGatewayProxyResponseEvent convert(List<Status> middlewareResults) {
        var responseEvent = new APIGatewayProxyResponseEvent();
        var headers = new HashMap<String, String>();

        headers.put(HeaderKey.CONTENT_TYPE, HeaderValue.APPLICATION_JSON);
        responseEvent.setHeaders(headers);

        for (var res : middlewareResults)
            if (res != null && res.getCode().startsWith("ERR")) {
                responseEvent.setStatusCode(res.getStatusCode());
                responseEvent.setBody(res.toString());
                return responseEvent;
            }
        return new APIGatewayProxyResponseEvent();
    }
}
