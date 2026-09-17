package org.xcore.plugin.map.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class MapContentHashTest {
    private static final String ABC_HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir Path directory;

    @Test
    void hashesBytesUsingSha256NotFileName() throws IOException {
        Path file = directory.resolve("original.msav");
        Files.writeString(file, "abc", StandardCharsets.UTF_8);
        MapContentHash original;
        try (var input = Files.newInputStream(file)) {
            original = MapContentHash.digest(input);
        }
        assertEquals(ABC_HASH, original.asHex());
        Path renamed = Files.move(file, directory.resolve("renamed.msav"));
        try (var input = Files.newInputStream(renamed)) {
            assertEquals(original, MapContentHash.digest(input));
        }
        Files.writeString(renamed, "abcd", StandardCharsets.UTF_8);
        try (var input = Files.newInputStream(renamed)) {
            assertNotEquals(original, MapContentHash.digest(input));
        }
    }

    @Test
    void parsesHexCaseInsensitivelyAndUsesValueEquality() {
        var hash = MapContentHash.fromHex(ABC_HASH.toUpperCase(java.util.Locale.ROOT));
        assertEquals(ABC_HASH, hash.asHex());
        assertEquals("ba7816bf8f01", hash.shortHex());
        assertEquals(1, new HashSet<>(java.util.List.of(hash, MapContentHash.fromHex(ABC_HASH))).size());
    }

    @Test
    void rejectsMalformedDigestsRatherThanTreatingNamesAsIds() {
        for (String invalid : new String[]{"", "map.msav", "0".repeat(63), "0".repeat(65), "z".repeat(64), " " + ABC_HASH}) {
            assertThrows(IllegalArgumentException.class, () -> MapContentHash.fromHex(invalid));
        }
        assertThrows(NullPointerException.class, () -> MapContentHash.fromHex(null));
        assertThrows(IllegalArgumentException.class, () -> MapContentHash.fromBytes(new byte[31]));
        assertThrows(IllegalArgumentException.class, () -> MapContentHash.fromBytes(new byte[33]));
    }

    @Test
    void doesNotRetainMutableDigestBytes() {
        byte[] bytes = HexFormat.of().parseHex(ABC_HASH);
        var hash = MapContentHash.fromBytes(bytes);
        bytes[0] = 0;
        assertEquals(ABC_HASH, hash.asHex());
    }

    @Test
    void consumesCompleteStreamAcrossShortReadsWithoutClosingCallerResource() throws IOException {
        var input = new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)) {
            boolean closed;
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(length, 1));
            }
            @Override public void close() {
                closed = true;
            }
        };
        assertEquals(ABC_HASH, MapContentHash.digest(input).asHex());
        assertFalse(input.closed);
    }

    @Test
    void propagatesReadErrorsRatherThanReturningPartialHash() {
        var input = new java.io.InputStream() {
            @Override public int read() throws IOException {
                throw new IOException("Read failed");
            }
        };
        assertThrows(IOException.class, () -> MapContentHash.digest(input));
    }
}
