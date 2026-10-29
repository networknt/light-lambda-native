package com.networknt.aws.lambda.handler.middleware.security;

import org.apache.commons.codec.binary.Base64;

import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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
        ByteBuffer encoded = UTF_8.encode(CharBuffer.wrap(auth, 6, auth.length()));
        byte[] payload = null;
        byte[] decoded = null;
        try {
            payload = new byte[encoded.remaining()];
            encoded.get(payload);
            // Preserve the existing raw username:password compatibility and lenient Base64 decoder.
            if (separator(payload) >= 0) {
                decoded = payload;
            } else {
                decoded = Base64.decodeBase64(payload);
                byte[] normalized = normalizeUtf8(decoded);
                Arrays.fill(decoded, (byte) 0);
                decoded = normalized;
            }
            BasicAuthCredentials credentials = new BasicAuthCredentials(decoded);
            if (decoded == payload) payload = null;
            decoded = null; // Ownership moves to the closeable credentials.
            return credentials;
        } finally {
            encoded.clear();
            while (encoded.hasRemaining()) encoded.put((byte) 0);
            if (payload != null) Arrays.fill(payload, (byte) 0);
            if (decoded != null) Arrays.fill(decoded, (byte) 0);
        }
    }

    // Preserve the former new String(decoded, UTF_8) replacement semantics without a String.
    private static byte[] normalizeUtf8(byte[] decoded) {
        CharBuffer characters = UTF_8.decode(ByteBuffer.wrap(decoded));
        ByteBuffer normalized = null;
        try {
            normalized = UTF_8.encode(characters);
            byte[] result = new byte[normalized.remaining()];
            normalized.get(result);
            return result;
        } finally {
            characters.clear();
            while (characters.hasRemaining()) characters.put('\0');
            if (normalized != null) {
                normalized.clear();
                while (normalized.hasRemaining()) normalized.put((byte) 0);
            }
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
