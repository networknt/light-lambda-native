package com.networknt.aws.lambda.handler.middleware.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationSchemeCredentialsTest {
    @Test
    void credentialPresencePreservesSchemeAndTrimSemantics() {
        for (AuthorizationScheme scheme : new AuthorizationScheme[]{AuthorizationScheme.BASIC, AuthorizationScheme.BEARER}) {
            assertFalse(AuthorizationScheme.hasCredentials(null, scheme));
            assertFalse(AuthorizationScheme.hasCredentials(scheme.name(), scheme));
            assertFalse(AuthorizationScheme.hasCredentials(scheme.name() + "Secret", scheme));
            for (char whitespace = 0; whitespace <= ' '; whitespace++) {
                String prefix = scheme.name() + " " + whitespace;
                assertFalse(AuthorizationScheme.hasCredentials(prefix, scheme));
                assertTrue(AuthorizationScheme.hasCredentials(prefix + "credential", scheme));
            }
            assertTrue(AuthorizationScheme.hasCredentials(scheme.name().toLowerCase(java.util.Locale.ROOT) + " x", scheme));
            assertTrue(AuthorizationScheme.hasCredentials(scheme.name() + " \u00a0", scheme));
        }
        assertFalse(AuthorizationScheme.hasCredentials("Basic x", AuthorizationScheme.BEARER));
        assertFalse(AuthorizationScheme.hasCredentials("Basic x", AuthorizationScheme.UNKNOWN));
    }
}
