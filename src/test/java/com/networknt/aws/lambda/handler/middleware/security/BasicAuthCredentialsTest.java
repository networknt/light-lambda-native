package com.networknt.aws.lambda.handler.middleware.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class BasicAuthCredentialsTest {
    @Test
    void preservesRawAndEncodedUnicodeCredentialsAndClearsOwnedBuffers() throws Exception {
        String pair = "user:secret-ë:extra";
        for (String payload : new String[]{pair, Base64.getEncoder().encodeToString(pair.getBytes(UTF_8))}) {
            BasicAuthCredentials credentials = BasicAuthCredentials.parse("Basic " + payload);
            byte[] decoded = buffer(credentials, "decoded");
            byte[] password = buffer(credentials, "password");
            try (credentials) {
                assertTrue(credentials.hasSeparator());
                assertEquals("user", credentials.username());
                assertFalse(credentials.isPasswordEmpty());
                assertTrue(credentials.matches("secret-ë:extra"));
                assertFalse(credentials.matches("secret-ë"));
                assertFalse(credentials.matches(null));
                assertEquals("secret-ë:extra", credentials.ldapPassword());
            }
            assertZero(decoded);
            assertZero(password);
            credentials.close();
            assertZero(password);
            assertEquals("user:secret-ë:extra", pair); // Borrowed immutable inputs are not erased.
        }
    }

    @Test
    void clearsMalformedEmptyAndExceptionalScopes() throws Exception {
        for (String payload : new String[]{"no-colon", "user:", "user:secret"}) {
            String header = "Basic " + Base64.getEncoder().encodeToString(payload.getBytes(UTF_8));
            BasicAuthCredentials credentials = BasicAuthCredentials.parse(header);
            byte[] decoded = buffer(credentials, "decoded");
            byte[] password = buffer(credentials, "password");
            assertThrows(IllegalStateException.class, () -> {
                try (credentials) {
                    assertEquals(payload.contains(":"), credentials.hasSeparator());
                    assertEquals(!payload.endsWith("secret"), credentials.isPasswordEmpty());
                    throw new IllegalStateException("synthetic failure");
                }
            });
            assertZero(decoded);
            assertZero(password);
        }
    }

    private static byte[] buffer(BasicAuthCredentials credentials, String name) throws Exception {
        var field = BasicAuthCredentials.class.getDeclaredField(name);
        field.setAccessible(true);
        return (byte[]) field.get(credentials);
    }

    private static void assertZero(byte[] buffer) {
        for (byte value : buffer) assertEquals(0, value, "Owned credential buffer was not cleared");
    }
}
