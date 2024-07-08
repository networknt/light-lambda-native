package com.networknt.aws.lambda.middleware.security;

import com.amazonaws.services.lambda.runtime.Context;
import com.networknt.apikey.ApiKeyConfig;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.middleware.security.ApiKeyMiddleware;
import com.networknt.aws.lambda.handler.middleware.security.BasicAuthMiddleware;
import com.networknt.aws.lambda.handler.middleware.specification.OpenApiMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.basicauth.BasicAuthConfig;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.MapUtil;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.networknt.aws.lambda.handler.middleware.audit.AuditMiddleware.AUDIT_ATTACHMENT_KEY;
import static java.nio.charset.StandardCharsets.UTF_8;

public class BasicAuthMiddlewareTest {

    private static String encodeCredentialsFullFormat(String username, String password, String separator) {
        String cred;
        if(password != null) {
            cred = username + separator + password;
        } else {
            cred = username;
        }
        String encodedValue;
        byte[] encodedBytes = Base64.encodeBase64(cred.getBytes(UTF_8));
        encodedValue = new String(encodedBytes, UTF_8);
        return encodedValue;
    }

    private static String encodeCredentials(String username, String password) {
        return encodeCredentialsFullFormat(username, password, ":");
    }

    /**
     * Test with right credentials but incorrect path. Expect 401 status code.
     */
    @Test
    public void testWithRightCredentialsWrongPath() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("user1", "user1pass"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10071", status.getCode());
    }

    /**
     * Test with right credentials and correct path. Expect 200 status code.
     */
    @Test
    public void testWithRightCredentialsRightPath() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("user2", "password"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(200, status.getStatusCode());
    }

    /**
     * Test with no token and correct path. Expect 401 status code.
     */
    @Test
    public void testMissingToken() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // add the X-Traceability-Id to the header
        MapUtil.delValueIgnoreCase(requestEvent.getHeaders(), HeaderKey.AUTHORIZATION);
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10002", status.getCode());
    }

    /**
     * Test invalid basic header credential info. Expect 401 status code.
     */
    @Test
    public void testInvalidBasicHeaderCredentialInfo() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentialsFullFormat("user1", "user1pass", "/"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10046", status.getCode());
    }

    /**
     * Test invalid basic header credential info. Expect 401 status code.
     */
    @Test
    public void testInvalidBasicHeaderPrefixText() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "Bearer " + encodeCredentials("user1", "user1pass"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10046", status.getCode());
    }

    /**
     * Test invalid username. Expect 401 status code.
     */
    @Test
    public void testInvalidUsername() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("user3", "user1pass"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10047", status.getCode());
    }

    /**
     * Test invalid password. Expect 401 status code.
     */
    @Test
    public void testInvalidPassword() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("user2", "ppp"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10047", status.getCode());
    }

    /**
     * Test "Basic " as the authorization header. Expect 401 status code.
     */
    @Test
    public void testBasicWithSpace() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // add the X-Traceability-Id to the header
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC ");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthConfig config = BasicAuthConfig.load("basic-auth");
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware(config);
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR12003", status.getCode());
    }

}
