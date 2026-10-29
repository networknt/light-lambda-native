package com.networknt.aws.lambda.handler.middleware.security;

import org.apache.commons.codec.binary.Base64;

import java.security.MessageDigest;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
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
        byte[] payload = encodeUtf8(CharBuffer.wrap(auth, 6, auth.length()));
        byte[] decoded = null;
        try {
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
            if (payload != null) Arrays.fill(payload, (byte) 0);
            if (decoded != null) Arrays.fill(decoded, (byte) 0);
        }
    }

    // Caller-supplied output prevents convenience charset APIs from discarding grown buffers.
    private static byte[] encodeUtf8(CharBuffer characters) {
        var encoder = UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        int capacity = Math.multiplyExact(characters.remaining(), (int) Math.ceil(encoder.maxBytesPerChar()));
        byte[] bytes = new byte[capacity];
        try {
            ByteBuffer output = ByteBuffer.wrap(bytes);
            requireUnderflow(encoder.encode(characters, output, true));
            requireUnderflow(encoder.flush(output));
            return Arrays.copyOf(bytes, output.position());
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    // Preserve the former new String(decoded, UTF_8) replacement semantics without a String.
    private static byte[] normalizeUtf8(byte[] decoded) {
        var decoder = UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        int capacity = Math.multiplyExact(decoded.length, (int) Math.ceil(decoder.maxCharsPerByte()));
        char[] characters = new char[capacity];
        try {
            CharBuffer output = CharBuffer.wrap(characters);
            requireUnderflow(decoder.decode(ByteBuffer.wrap(decoded), output, true));
            requireUnderflow(decoder.flush(output));
            output.flip();
            return encodeUtf8(output);
        } finally {
            Arrays.fill(characters, '\0');
        }
    }

    private static void requireUnderflow(CoderResult result) {
        if (!result.isUnderflow()) throw new IllegalStateException("Unexpected UTF-8 conversion result");
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
        byte[] expected = encodeUtf8(CharBuffer.wrap(configuredPassword));
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
