package com.networknt.aws.lambda.handler.middleware.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.basicauth.UserAuth;
import com.networknt.status.Status;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class BasicAuthPrivacyTest {
    private static final java.util.List<String> ENCODED_CANARIES = new java.util.ArrayList<>();
    private static final String SECRET = "lambda-secret-canary:ü";

    private static class LdapFixture extends BasicAuthMiddleware {
        boolean accepted;
        boolean fail;
        int calls;
        LdapFixture() { super("basic-auth-memory"); }
        @Override protected boolean handleLdapAuth(UserAuth user, String password) {
            calls++;
            assertEquals(SECRET, password);
            if (fail) throw new IllegalStateException("synthetic LDAP failure");
            return accepted;
        }
    }

    @Test
    void reportedA25PathsRemainPrivateAtTraceAcrossLocalAndLdapOutcomes() {
        ENCODED_CANARIES.clear();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Level previous = root.getLevel();
        var logs = new ListAppender<ILoggingEvent>();
        logs.start();
        root.addAppender(logs);
        root.setLevel(Level.TRACE);
        try {
            var middleware = new LdapFixture();
            for (boolean encoded : new boolean[]{false, true}) {
                check(middleware, "local-user:" + SECRET, encoded, "/allowed", 200);
                check(middleware, "local-user:" + SECRET, encoded, "/denied", 401);
                check(middleware, "local-user:wrong-" + SECRET, encoded, "/allowed", 401);
                check(middleware, "unknown-user:" + SECRET, encoded, "/allowed", 401);
                check(middleware, "local-user:", encoded, "/allowed", 401);
                check(middleware, "anonymous:" + SECRET, encoded, "/allowed", 401);
                middleware.accepted = false;
                check(middleware, "ldap-user:" + SECRET, encoded, "/allowed", 401);
                middleware.accepted = true;
                check(middleware, "ldap-user:" + SECRET, encoded, "/allowed", 200);
            }
            check(middleware, "malformed-secret-canary", true, "/allowed", 401);
            middleware.fail = true;
            assertThrows(IllegalStateException.class, () -> middleware.execute(exchange(
                    "Basic " + Base64.getEncoder().encodeToString(("ldap-user:" + SECRET).getBytes(UTF_8)), "/allowed")));
            assertEquals(5, middleware.calls);
            assertTrue(logs.list.stream().anyMatch(event -> event.getLevel() == Level.TRACE));
            for (ILoggingEvent event : logs.list) {
                privateText(event.getFormattedMessage());
                privateText(Arrays.deepToString(event.getArgumentArray()));
                privateText(String.valueOf(event.getMDCPropertyMap()));
                privateThrowable(event.getThrowableProxy());
            }
        } finally {
            root.setLevel(previous);
            root.detachAppender(logs);
            logs.stop();
        }
    }

    private static void check(BasicAuthMiddleware middleware, String pair, boolean encoded, String path, int expected) {
        ENCODED_CANARIES.add(Base64.getEncoder().encodeToString(pair.getBytes(UTF_8)));
        String header = "Basic " + (encoded ? Base64.getEncoder().encodeToString(pair.getBytes(UTF_8)) : pair);
        var exchange = exchange(header, path);
        Status result = middleware.execute(exchange);
        assertEquals(expected, result.getStatusCode());
        privateText(result.toString());
    }

    private static LightLambdaExchange exchange(String header, String path) {
        var request = TestUtils.createTestRequestEvent();
        request.setPath(path);
        request.getHeaders().put("Authorization", header);
        var exchange = new LightLambdaExchange(new LambdaContext("privacy-fixture"), null);
        exchange.setInitialRequest(request);
        return exchange;
    }

    private static void privateText(String text) {
        assertFalse(text.contains("secret-canary"), text);
        for (String canary : ENCODED_CANARIES) assertFalse(text.contains(canary), text);
        assertFalse(text.contains(Base64.getEncoder().encodeToString(("local-user:" + SECRET).getBytes(UTF_8))), text);
        assertFalse(text.contains(Base64.getEncoder().encodeToString(("ldap-user:" + SECRET).getBytes(UTF_8))), text);
    }

    private static void privateThrowable(IThrowableProxy throwable) {
        if (throwable == null) return;
        privateText(String.valueOf(throwable.getMessage()));
        privateThrowable(throwable.getCause());
        if (throwable.getSuppressed() != null) for (var suppressed : throwable.getSuppressed()) privateThrowable(suppressed);
    }
}
