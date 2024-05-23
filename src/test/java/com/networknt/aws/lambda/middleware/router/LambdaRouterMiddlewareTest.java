package com.networknt.aws.lambda.middleware.router;

import com.networknt.aws.lambda.handler.middleware.router.LambdaRouterMiddleware;
import com.networknt.router.RouterConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LambdaRouterMiddlewareTest {
    @Test
    public void testPathRewrite() {
        LambdaRouterMiddleware lambdaRouterMiddleware = new LambdaRouterMiddleware();
        RouterConfig config = RouterConfig.load();
        String targetPath = lambdaRouterMiddleware.createRouterRequestPath(config.getUrlRewriteRules(), "/v1/pets");
        assertEquals("/Stage/v1/pets", targetPath, "The target path should be /Stage/v1/pets");
    }
}
