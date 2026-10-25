package com.networknt.aws.lambda;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class Jackson3NativeCompatibilityTest {
    private ObjectMapper mainMapper() throws Exception {
        Field field = Main.class.getDeclaredField("OBJECT_MAPPER");
        field.setAccessible(true);
        return (ObjectMapper) field.get(null);
    }

    @Test
    void runtimeMapperKeepsPermissiveEventBinding() throws Exception {
        APIGatewayProxyRequestEvent event = mainMapper().readValue(
                "{\"path\":\"/v1/pets\",\"httpMethod\":\"POST\",\"futureField\":true} trailing",
                APIGatewayProxyRequestEvent.class);
        assertEquals("/v1/pets", event.getPath());
        assertEquals("POST", event.getHttpMethod());
    }

    @Test
    void responseRetainsJackson2ShapeAndRoundTrips() throws Exception {
        ObjectMapper mapper = mainMapper();
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
        assertDoesNotThrow(() -> mainMapper().readTree(nested));
    }
}
