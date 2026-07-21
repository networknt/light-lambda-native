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
        PathTemplateMatcher<String> matcher = methodToMatcherMap.get("get");
        Assertions.assertNotNull(matcher, "no matcher registered for the 'get' method");
        PathTemplateMatcher.PathMatchResult<String> result = matcher.match("/v1/pets/123");
        Assertions.assertNotNull(result, "no path template matched /v1/pets/123");
        Assertions.assertEquals("PetsPetIdGetFunction", result.getValue());
    }
}
