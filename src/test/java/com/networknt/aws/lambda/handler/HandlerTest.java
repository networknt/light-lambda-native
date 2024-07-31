package com.networknt.aws.lambda.handler;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HandlerTest {
    @Test
    public void testInitHandler() {
        Handler.init();
        Assertions.assertEquals(25, Handler.getHandlers().size());
    }
}
