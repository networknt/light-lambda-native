package com.networknt.aws.lambda.middleware.invoke;

import com.networknt.utility.PathTemplateMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class MockLambdaFunctionInvokerTest {
    @Test
    public void testConstructor() {
        MockLambdaFunctionInvoker mockLambdaFunctionInvoker = new MockLambdaFunctionInvoker();
        Map<String, PathTemplateMatcher<String>> methodToMatcherMap = MockLambdaFunctionInvoker.methodToMatcherMap;
        Assertions.assertFalse(methodToMatcherMap.isEmpty());
        PathTemplateMatcher.PathMatchResult<String> result = methodToMatcherMap.get("get").match("/v1/pets/123");
        Assertions.assertEquals("PetsPetIdGetFunction", result.getValue());
    }
}
