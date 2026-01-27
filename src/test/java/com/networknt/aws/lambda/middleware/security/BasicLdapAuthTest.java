package com.networknt.aws.lambda.middleware.security;

import com.amazonaws.services.lambda.runtime.Context;
import com.networknt.aws.lambda.InvocationResponse;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.aws.lambda.handler.middleware.security.BasicAuthMiddleware;
import com.networknt.aws.lambda.utility.HeaderKey;
import com.networknt.basicauth.BasicAuthConfig;
import com.networknt.status.Status;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BasicLdapAuthTest {
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
     * Test with an LDAP user and wrong path. Expect 403 status code.
     * Note: You need to set up the LDAP and the user in the LDAP server to run this test.
     *
     */
    @Disabled
    @Test
    public void testWithADAuthWrongPath() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v1/address");
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("ldapUser", "password"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-ldap");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(403, status.getStatusCode());
    }

    /**
     * Test with an LDAP user and right path. Expect 200 status code.
     * Note: You need to set up the LDAP and the user in the LDAP server to run this test.
     *
     */
    @Disabled
    @Test
    public void testWithADAuthRightPath() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("ldapUser", "password"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-ldap");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(200, status.getStatusCode());
    }

    /**
     * Test without Bearer header and right path. Expect 401 status code.
     */
    @Test
    public void testMissingToken() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        // requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "Bearer token");
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-ldap");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10046", status.getCode());
    }

    /**
     * Test with invalid BASIC credentials and right path. Expect 401 status code.
     */
    @Test
    public void testInvalidBasicHeaderCredentialInfo() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentialsFullFormat("user1", "user1pass", "/"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-ldap");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10046", status.getCode());
    }

    /**
     * Test with Bearer header with credentials and right path. Expect 401 status code.
     */
    @Test
    public void testInvalidBasicHeaderPrefixText() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "Bearer " + encodeCredentials("user1", "user1pass"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-ldap");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10046", status.getCode());
    }

    /**
     * Test with invalid username  and right path. Expect 401 status code.
     */
    @Test
    public void testInvalidUsername() {
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("user3", "user1pass"));
        InvocationResponse invocation = InvocationResponse.builder()
                .requestId("12345")
                .event(requestEvent)
                .build();
        Context lambdaContext = new LambdaContext(invocation.getRequestId());
        final var exchange = new LightLambdaExchange(lambdaContext, null);
        exchange.setInitialRequest(requestEvent);
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth-ldap");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10047", status.getCode());
    }

}
