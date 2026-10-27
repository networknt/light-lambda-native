package com.networknt.aws.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Jackson3NativeCompatibilityTest {

    @Test
    void runtimeMapperKeepsPermissiveEventBinding() throws Exception {
        APIGatewayProxyRequestEvent event = LambdaRuntimeMappers.mapper().readValue(
                "{\"path\":\"/v1/pets\",\"httpMethod\":\"POST\",\"futureField\":true} trailing",
                APIGatewayProxyRequestEvent.class);
        assertEquals("/v1/pets", event.getPath());
        assertEquals("POST", event.getHttpMethod());
    }

    @Test
    void responseRetainsJackson2ShapeAndRoundTrips() throws Exception {
        ObjectMapper mapper = LambdaRuntimeMappers.mapper();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withStatusCode(200).withBody("{\"ok\":true}").withIsBase64Encoded(false);
        String json = mapper.writeValueAsString(response);
        assertEquals("{\"statusCode\":200,\"headers\":null,\"multiValueHeaders\":null,"
                + "\"body\":\"{\\\"ok\\\":true}\",\"isBase64Encoded\":false}", json);
        assertEquals(response, mapper.readValue(json, APIGatewayProxyResponseEvent.class));
    }

    @Test
    void runtimeMapperRetainsJackson2StreamDepth() throws Exception {
        String nested = "[".repeat(600) + "0" + "]".repeat(600);
        assertDoesNotThrow(() -> LambdaRuntimeMappers.mapper().readTree(nested));
    }
}
