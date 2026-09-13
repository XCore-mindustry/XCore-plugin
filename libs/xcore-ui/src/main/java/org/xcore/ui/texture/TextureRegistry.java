package org.xcore.ui.texture;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Content-addressable registry for server-streamed textures.
 *
 * <p>Every texture is named {@code net-xcore_<hash16>} where hash16 is the first
 * 16 hex chars of the SHA-256 of its PNG bytes. Identical bytes therefore map
 * to identical names, making the registry self-deduplicating.
 *
 * <p>Delivery is tracked per connection so each PNG crosses the wire exactly
 * once per session. Actual transmission is delegated to a {@link TextureSender}
 * callback (the host binds {@code Vars.netServer.sendTexture}); the registry
 * itself performs no network I/O and is unit-testable.
 */
public final class TextureRegistry {

    /** Region names on the client atlas carry this prefix. */
    public static final String NET_PREFIX = "net-";

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** Sends (connectionId, regionName, pngBytes) to a single client. */
    @FunctionalInterface
    public interface TextureSender {
        void send(String connectionId, String regionName, byte[] pngBytes);
    }

    private final Map<String, byte[]> textures = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> delivered = new ConcurrentHashMap<>();
    private final TextureSender sender;

    public TextureRegistry(TextureSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /** Registers PNG bytes and returns the region name (with {@code net-} prefix). */
    public String register(byte[] pngBytes) {
        Objects.requireNonNull(pngBytes, "pngBytes");
        String name = NET_PREFIX + "xcore_" + sha256Hex16(pngBytes);
        textures.put(name, pngBytes);
        return name;
    }

    /** Returns registered bytes for a region name, or null. */
    public byte[] bytes(String regionName) {
        return textures.get(regionName);
    }

    /**
     * Ensures the connection has received the texture, streaming it if needed.
     * Returns the region name for embedding into a {@code VImage}.
     */
    public String ensureDelivered(String connectionId, byte[] pngBytes) {
        String name = register(pngBytes);
        ensureDeliveredName(connectionId, name);
        return name;
    }

    /** Streams a registered texture if this connection has not received it yet. */
    public void ensureDeliveredName(String connectionId, String regionName) {
        byte[] bytes = textures.get(regionName);
        if (bytes == null) {
            throw new IllegalArgumentException("Unknown texture region: " + regionName);
        }
        Map<String, Boolean> seen = delivered.computeIfAbsent(regionName, k -> new ConcurrentHashMap<>());
        if (seen.putIfAbsent(connectionId, Boolean.TRUE) == null) {
            sender.send(connectionId, regionName, bytes);
        }
    }

    /** Drops the delivery marker so the texture re-streams on next use (e.g. after reconnect). */
    public void resetDelivery(String connectionId) {
        for (Map<String, Boolean> m : delivered.values()) {
            m.remove(connectionId);
        }
    }

    /** Number of unique textures currently held. */
    public int size() {
        return textures.size();
    }

    static String sha256Hex16(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(HEX[(digest[i] >> 4) & 0xF]).append(HEX[digest[i] & 0xF]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
