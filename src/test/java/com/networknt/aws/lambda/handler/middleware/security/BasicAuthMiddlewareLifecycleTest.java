package com.networknt.aws.lambda.handler.middleware.security;

import com.networknt.aws.lambda.LambdaContext;
import com.networknt.aws.lambda.LightLambdaExchange;
import com.networknt.aws.lambda.TestUtils;
import com.networknt.basicauth.UserAuth;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class BasicAuthMiddlewareLifecycleTest {
    private static final String SECRET = "q7V9p2Z8~R4m6N1t3~H5k0W2s9ü";

    private static class ObservedMiddleware extends BasicAuthMiddleware {
        final List<byte[]> buffers = new ArrayList<>();
        boolean ldapAccepted;
        boolean ldapThrows;
        ObservedMiddleware() { super("basic-auth-memory"); }

        @Override BasicAuthCredentials parseCredentials(String auth) {
            BasicAuthCredentials credentials = super.parseCredentials(auth);
            for (String name : new String[]{"decoded", "password"}) {
                try {
                    var field = BasicAuthCredentials.class.getDeclaredField(name);
                    field.setAccessible(true);
                    buffers.add((byte[]) field.get(credentials));
                } catch (ReflectiveOperationException failure) {
                    throw new AssertionError(failure);
                }
            }
            return credentials;
        }

        @Override protected boolean handleLdapAuth(UserAuth user, String password) {
            assertEquals(SECRET, password);
            if (ldapThrows) throw new IllegalStateException("synthetic LDAP failure");
            return ldapAccepted;
        }

        void assertClosed() {
            assertEquals(2, buffers.size(), "Observe buffers from exactly one real middleware parse");
            for (byte[] buffer : buffers) for (byte value : buffer) assertEquals(0, value);
            buffers.clear();
        }
    }

    @Test
    void actualBasicHandlerClosesOnEveryAuthenticationAndAuthorizationReturn() {
        var middleware = new ObservedMiddleware();
        for (boolean encoded : new boolean[]{false, true}) {
            check(middleware, "local-user:" + SECRET, encoded, "/allowed", 200);
            check(middleware, "local-user:" + SECRET, encoded, "/denied", 401);
            check(middleware, "local-user:wrong", encoded, "/allowed", 401);
            check(middleware, "local-user:", encoded, "/allowed", 401);
            check(middleware, "unknown-user:" + SECRET, encoded, "/allowed", 401);
            check(middleware, "anonymous:" + SECRET, encoded, "/allowed", 401);
            check(middleware, "bearer:" + SECRET, encoded, "/allowed", 401);
            middleware.ldapAccepted = false;
            check(middleware, "ldap-user:" + SECRET, encoded, "/allowed", 401);
            middleware.ldapAccepted = true;
            check(middleware, "ldap-user:" + SECRET, encoded, "/allowed", 200);
            check(middleware, "ldap-user:" + SECRET, encoded, "/denied", 401);
        }
        check(middleware, "missing-colon", true, "/allowed", 401);
    }

    @Test
    void actualBasicHandlerClosesWhenLdapThrows() {
        var middleware = new ObservedMiddleware();
        middleware.ldapThrows = true;
        String header = "Basic " + Base64.getEncoder().encodeToString(("ldap-user:" + SECRET).getBytes(UTF_8));
        assertThrows(IllegalStateException.class, () -> middleware.handleBasicAuth(exchange(header), "/allowed", header));
        middleware.assertClosed();
    }

    @Test
    void unicodeSchemeAndMalformedUtf8RemainCompatibleThroughExecute() {
        var middleware = new ObservedMiddleware();
        String header = "Bas\u0131c local-user:" + SECRET;
        assertEquals(200, middleware.execute(exchange(header)).getStatusCode());
        middleware.assertClosed();
        byte[] prefix = "replacement-user:".getBytes(UTF_8);
        byte[] pair = java.util.Arrays.copyOf(prefix, prefix.length + 2);
        pair[prefix.length] = (byte) 0xc3;
        pair[prefix.length + 1] = '(';
        header = "Basic " + Base64.getEncoder().encodeToString(pair);
        assertEquals(200, middleware.execute(exchange(header)).getStatusCode());
        middleware.assertClosed();
    }

    private static void check(ObservedMiddleware middleware, String pair, boolean encoded, String path, int expected) {
        String header = "Basic " + (encoded ? Base64.getEncoder().encodeToString(pair.getBytes(UTF_8)) : pair);
        assertEquals(expected, middleware.handleBasicAuth(exchange(header), path, header).getStatusCode());
        middleware.assertClosed();
    }

    private static LightLambdaExchange exchange(String header) {
        var request = TestUtils.createTestRequestEvent();
        request.setPath("/allowed");
        request.getHeaders().put("Authorization", header);
        var exchange = new LightLambdaExchange(new LambdaContext("lifecycle-fixture"), null);
        exchange.setInitialRequest(request);
        return exchange;
    }
}
