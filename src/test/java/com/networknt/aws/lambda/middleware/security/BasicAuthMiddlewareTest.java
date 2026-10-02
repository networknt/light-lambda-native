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
import com.networknt.basicauth.UserAuth;
import com.networknt.aws.lambda.handler.middleware.security.UnifiedSecurityMiddleware;
import com.networknt.status.Status;
import com.networknt.utility.Constants;
import com.networknt.utility.MapUtil;
import org.apache.commons.codec.binary.Base64;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

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

    @Test
    public void testEmptyConfiguredPasswordIsRejected() {
        Assertions.assertEquals("", BasicAuthConfig.load().getUsers().get("blankPassword").getPassword());
        var requestEvent = TestUtils.createTestRequestEvent();
        requestEvent.setPath("/v2/pet");
        requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, "BASIC " + encodeCredentials("blankPassword", ""));
        var exchange = new LightLambdaExchange(new LambdaContext("empty-password"), null);
        exchange.setInitialRequest(requestEvent);
        Status status = new BasicAuthMiddleware("basic-auth").execute(exchange);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR10047", status.getCode());
    }

    @Test
    public void testShortMalformedAuthorizationHeadersAreRejected() {
        for (String header : new String[] {"x", "Basic eA", "Bearer", "Bearer x", "BasicSecret", "Basic"}) {
            var requestEvent = TestUtils.createTestRequestEvent();
            requestEvent.setPath("/v2/pet");
            requestEvent.getHeaders().put(HeaderKey.AUTHORIZATION, header);
            var exchange = new LightLambdaExchange(new LambdaContext("short-header"), null);
            exchange.setInitialRequest(requestEvent);
            Status status = new BasicAuthMiddleware("basic-auth").execute(exchange);
            Assertions.assertEquals(401, status.getStatusCode(), header);
            Assertions.assertEquals(header.equals("Basic eA") || header.equals("Bearer x")
                    ? "ERR10046" : "ERR12003", status.getCode(), header);
        }
    }

    @Test
    public void testBareBearerRejectedWhenBearerTokensAllowed() {
        var middleware = new BasicAuthMiddleware("basic-auth-bearer");
        Assertions.assertTrue(BasicAuthConfig.load("basic-auth-bearer").isAllowBearerToken());
        for (String header : new String[] {"Bearer", "Bearer ", "bearer   "}) {
            Status status = middleware.execute(exchangeFor(header));
            Assertions.assertEquals(401, status.getStatusCode(), header);
            Assertions.assertEquals("ERR12003", status.getCode(), header);
        }
    }

    private static LightLambdaExchange exchangeFor(String header) {
        var request = TestUtils.createTestRequestEvent();
        request.setPath("/v2/pet");
        request.getHeaders().put(HeaderKey.AUTHORIZATION, header);
        var exchange = new LightLambdaExchange(new LambdaContext("security-review"), null);
        exchange.setInitialRequest(request);
        return exchange;
    }

    private static class RecordingLdapMiddleware extends BasicAuthMiddleware {
        int ldapCalls;

        RecordingLdapMiddleware() {
            super("basic-auth-ldap");
        }

        @Override
        protected boolean handleLdapAuth(UserAuth user, String password) {
            ldapCalls++;
            return true;
        }
    }

    @Test
    public void testLdapRejectsEmptyPasswordsAndReservedUsersBeforeBind() {
        Assertions.assertTrue(BasicAuthConfig.load("basic-auth-ldap").isEnableAD());
        var middleware = new RecordingLdapMiddleware();
        for (String[] credentials : new String[][] {
                {"ldapUser", ""}, {"anonymous", "password"}, {"bearer", "password"}}) {
            Status status = middleware.execute(exchangeFor("Basic " + encodeCredentials(credentials[0], credentials[1])));
            Assertions.assertEquals("ERR10047", status.getCode());
            Assertions.assertEquals(401, status.getStatusCode());
        }
        Assertions.assertEquals(0, middleware.ldapCalls);
        // Positive control proves this configuration actually dispatches ordinary identities to LDAP.
        Assertions.assertEquals(200, middleware.execute(exchangeFor("Basic " + encodeCredentials("ldapUser", "password"))).getStatusCode());
        Assertions.assertEquals(1, middleware.ldapCalls);
    }

    @Test
    public void testPublicBasicEntryPointRejectsMalformedHeaders() {
        var middleware = new BasicAuthMiddleware("basic-auth");
        for (String header : new String[] {null, "", "x", "Basic", "Basic ", "BasicSecret", "Bearer x"}) {
            Status status = middleware.handleBasicAuth(exchangeFor("ignored"), "/v2/pet", header);
            Assertions.assertEquals("ERR12003", status.getCode());
            Assertions.assertEquals(401, status.getStatusCode());
        }
    }

    @Test
    public void testUnifiedRejectsMalformedHeadersWithoutEchoingCredentials() {
        var middleware = new UnifiedSecurityMiddleware("unified-security-review");
        for (String header : new String[] {"x", "abcde", "Secret credential-material", "Basic", "Basic ", "Bearer", "Bearer ", "BasicSecret"}) {
            Status status = middleware.execute(exchangeFor(header));
            Assertions.assertEquals("ERR12003", status.getCode(), header);
            Assertions.assertEquals(401, status.getStatusCode(), header);
            Assertions.assertFalse(status.toString().contains(header), header);
        }
    }

    @Test
    public void testAuthorizationLogsDoNotEchoHeadersAndSanitizeDeniedPath() {
        Logger basicLogger = (Logger) LoggerFactory.getLogger(BasicAuthMiddleware.class);
        Logger unifiedLogger = (Logger) LoggerFactory.getLogger(UnifiedSecurityMiddleware.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        basicLogger.addAppender(appender);
        unifiedLogger.addAppender(appender);
        try {
            var basic = new BasicAuthMiddleware("basic-auth");
            var unified = new UnifiedSecurityMiddleware("unified-security-review");
            for (String header : new String[] {"abcde", "Secret credential-material"}) {
                basic.execute(exchangeFor(header));
                unified.execute(exchangeFor(header));
                Assertions.assertTrue(appender.list.stream().noneMatch(event -> event.getFormattedMessage().contains(header)));
            }
            var exchange = exchangeFor("Basic " + encodeCredentials("user1", "user1pass"));
            exchange.getRequest().setPath("/denied\r\nforged-line");
            Status status = basic.execute(exchange);
            Assertions.assertEquals("ERR10071", status.getCode());
            Assertions.assertFalse(status.toString().contains("\r") || status.toString().contains("\n"));
            Assertions.assertTrue(status.toString().contains("/denied  forged-line"));
            Assertions.assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage()
                    .equals("Request path '/denied  forged-line' is not authorized for user 'user1'")));
        } finally {
            basicLogger.detachAppender(appender);
            unifiedLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void testMixedCaseBasicSchemeAndColonInPasswordParsing() {
        var basic = new BasicAuthMiddleware("basic-auth");
        Assertions.assertEquals(200, basic.execute(exchangeFor("bAsIc " + encodeCredentials("user2", "password"))).getStatusCode());
        Assertions.assertEquals("ERR10047", basic.execute(exchangeFor("Basic " + encodeCredentials("user2", "password:extra"))).getCode());
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
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth");
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
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth");
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
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth");
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
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth");
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
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth");
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
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth");
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
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth");
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
        BasicAuthMiddleware basicAuthMiddleware = new BasicAuthMiddleware("basic-auth");
        Status status = basicAuthMiddleware.execute(exchange);
        Assertions.assertNotNull(status);
        Assertions.assertEquals(401, status.getStatusCode());
        Assertions.assertEquals("ERR12003", status.getCode());
    }

}
