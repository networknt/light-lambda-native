package com.networknt.aws.lambda.handler.middleware.security;

/** Scheme classification without retaining or exposing credential material. */
enum AuthorizationScheme {
    BASIC, BEARER, UNKNOWN;

    static AuthorizationScheme parse(String header) {
        if (header == null) return UNKNOWN;
        for (AuthorizationScheme scheme : new AuthorizationScheme[] {BASIC, BEARER}) {
            String prefix = scheme.name();
            if (header.regionMatches(true, 0, prefix, 0, prefix.length())
                    && (header.length() == prefix.length() || header.charAt(prefix.length()) == ' ')) {
                return scheme;
            }
        }
        return UNKNOWN;
    }

    static boolean hasCredentials(String header, AuthorizationScheme scheme) {
        if (scheme == UNKNOWN || parse(header) != scheme) return false;
        return hasTrimmedContent(header, scheme.name().length() + 1);
    }

    // Match String.trim() semantics without copying credential material into another String.
    static boolean hasTrimmedContent(String header, int start) {
        for (int i = start; i < header.length(); i++) {
            if (header.charAt(i) > ' ') return true;
        }
        return false;
    }
}
