package com.networknt.aws.lambda.middleware.proxy;

import com.networknt.utility.PathTemplateMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class MockLambdaProxyMiddlewareTest {
    @Test
    public void testConstructor() {
        MockLambdaProxyMiddleware mockLambdaProxyMiddleware = new MockLambdaProxyMiddleware();
        Map<String, PathTemplateMatcher<String>> methodToMatcherMap = MockLambdaProxyMiddleware.methodToMatcherMap;
        Assertions.assertFalse(methodToMatcherMap.isEmpty());
        PathTemplateMatcher.PathMatchResult<String> result = methodToMatcherMap.get("get").match("/v1/pets/123");
        Assertions.assertEquals("PetsPetIdGetFunction", result.getValue());
    }
}
