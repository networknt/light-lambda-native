package com.networknt.aws.lambda.handler;

import org.junit.Assert;
import org.junit.Test;

public class HandlerTest {
    @Test
    public void testInitHandler() {
        Handler.init();
        Assert.assertEquals(12, Handler.getHandlers().size());
    }

}
