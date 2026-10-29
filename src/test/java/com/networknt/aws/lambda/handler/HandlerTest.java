package com.networknt.aws.lambda.handler;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.util.SetSystemProperty;

@SetSystemProperty(key = "light_4j_config_password", value = "light")
public class HandlerTest {
    @Test
    public void testInitHandler() {
        Handler.init();
        Assertions.assertEquals(27, Handler.getHandlers().size());
    }
}
