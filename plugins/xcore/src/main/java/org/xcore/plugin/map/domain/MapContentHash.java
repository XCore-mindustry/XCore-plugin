package org.xcore.plugin.map.domain;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * SHA-256 of the exact file bytes, not a logical map ID. Metadata edits and re-saves
 * can change this value; a filename change cannot. Never use a short hash as identity.
 */
public record MapContentHash(String asHex) {
    public MapContentHash {
        Objects.requireNonNull(asHex, "asHex");
        if (asHex.length() != 64) {
            throw new IllegalArgumentException("SHA-256 must contain exactly 64 hexadecimal characters");
        }
        asHex = HexFormat.of().formatHex(HexFormat.of().parseHex(asHex));
    }

    public static MapContentHash fromHex(String hex) {
        return new MapContentHash(hex);
    }

    public static MapContentHash fromBytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length != 32) {
            throw new IllegalArgumentException("SHA-256 must contain exactly 32 bytes");
        }
        return new MapContentHash(HexFormat.of().formatHex(bytes));
    }

    /**
     * Consumes but does not close the caller-owned stream. May block: file streams
     * must only be supplied from a worker, never from the Mindustry tick thread.
     */
    public static MapContentHash digest(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return fromBytes(digest.digest());
    }

    public String shortHex() {
        return asHex.substring(0, 12);
    }

    @Override
    public String toString() {
        return asHex;
    }
}
