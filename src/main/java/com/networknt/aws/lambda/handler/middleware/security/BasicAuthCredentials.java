package com.networknt.aws.lambda.handler.middleware.security;

import org.apache.commons.codec.binary.Base64;

import java.security.MessageDigest;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Request-local, owned copies only; the original request and configuration remain borrowed. */
final class BasicAuthCredentials implements AutoCloseable {
    private final byte[] decoded;
    private final byte[] password;
    private final int separator;

    private BasicAuthCredentials(byte[] decoded) {
        this.decoded = decoded;
        separator = separator(decoded);
        password = separator < 0 ? new byte[0] : Arrays.copyOfRange(decoded, separator + 1, decoded.length);
    }

    static BasicAuthCredentials parse(String auth) {
        byte[] header = auth.getBytes(UTF_8);
        byte[] payload = null;
        byte[] decoded = null;
        try {
            payload = Arrays.copyOfRange(header, 6, header.length);
            // Preserve the existing raw username:password compatibility and lenient Base64 decoder.
            decoded = separator(payload) >= 0 ? payload : Base64.decodeBase64(payload);
            BasicAuthCredentials credentials = new BasicAuthCredentials(decoded);
            if (decoded == payload) payload = null;
            decoded = null; // Ownership moves to the closeable credentials.
            return credentials;
        } finally {
            Arrays.fill(header, (byte) 0);
            if (payload != null) Arrays.fill(payload, (byte) 0);
            if (decoded != null) Arrays.fill(decoded, (byte) 0);
        }
    }

    private static int separator(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) if (bytes[i] == ':') return i;
        return -1;
    }

    boolean hasSeparator() { return separator >= 0; }
    boolean isPasswordEmpty() { return password.length == 0; }
    String username() { return new String(decoded, 0, separator, UTF_8); }

    boolean matches(String configuredPassword) {
        if (configuredPassword == null) return false;
        byte[] expected = configuredPassword.getBytes(UTF_8);
        try {
            return MessageDigest.isEqual(password, expected);
        } finally {
            Arrays.fill(expected, (byte) 0);
        }
    }

    // The existing LDAP API/extension requires a String; only this branch creates it.
    String ldapPassword() { return new String(password, UTF_8); }

    @Override
    public void close() {
        Arrays.fill(decoded, (byte) 0);
        Arrays.fill(password, (byte) 0);
    }
}
