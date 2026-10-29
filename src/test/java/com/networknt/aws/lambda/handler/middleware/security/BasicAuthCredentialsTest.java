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

    @Test
    void schemeIsRemovedAtCharacterBoundaryIncludingUnicodeCaseVariants() {
        for (String scheme : new String[]{"Basic", "bAsIc", "Bas\u0131c", "Bas\u0130c", "Ba\u017fic"}) {
            for (String payload : new String[]{"user:password", Base64.getEncoder().encodeToString("user:password".getBytes(UTF_8))}) {
                assertEquals(AuthorizationScheme.BASIC, AuthorizationScheme.parse(scheme + " " + payload));
                try (var credentials = BasicAuthCredentials.parse(scheme + " " + payload)) {
                    assertEquals("user", credentials.username());
                    assertTrue(credentials.matches("password"));
                }
            }
        }
    }

    @Test
    void malformedUtf8RetainsHistoricalReplacementForLocalComparisonAndLdap() {
        for (byte[] malformed : new byte[][]{{(byte) 0xc3, '('}, {(byte) 0xe2}, {(byte) 0xff}}) {
            byte[] prefix = "user:".getBytes(UTF_8);
            byte[] pair = java.util.Arrays.copyOf(prefix, prefix.length + malformed.length);
            System.arraycopy(malformed, 0, pair, prefix.length, malformed.length);
            String previousPassword = new String(malformed, UTF_8);
            try (var credentials = BasicAuthCredentials.parse("Basic " + Base64.getEncoder().encodeToString(pair))) {
                assertEquals("user", credentials.username());
                assertTrue(credentials.matches(previousPassword));
                assertEquals(previousPassword, credentials.ldapPassword());
                assertFalse(credentials.matches("incorrect"));
            }
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
