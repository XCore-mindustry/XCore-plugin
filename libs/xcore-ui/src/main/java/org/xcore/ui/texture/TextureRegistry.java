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

        /**
         * Sends the texture if the connection can take it now. A {@code false}, or an exception,
         * leaves the texture undelivered, so the next {@link #ensureDeliveredName} tries again;
         * the default sends and answers {@code true}.
         */
        default boolean trySend(String connectionId, String regionName, byte[] pngBytes) {
            send(connectionId, regionName, pngBytes);
            return true;
        }
    }

    private final Map<String, byte[]> textures = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Boolean>> delivered = new ConcurrentHashMap<>();
    private final TextureSender sender;

    public TextureRegistry(TextureSender sender) {
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /**
     * Registers PNG bytes and returns the region name (with {@code net-} prefix). The registry keeps
     * a copy, so the name stays true to the bytes whatever the caller does with its array.
     */
    public String register(byte[] pngBytes) {
        Objects.requireNonNull(pngBytes, "pngBytes");
        // Hashed from the copy kept, so a caller writing to its array meanwhile cannot split them.
        byte[] copy = pngBytes.clone();
        String name = NET_PREFIX + "xcore_" + sha256Hex16(copy);
        textures.putIfAbsent(name, copy);
        return name;
    }

    /** Returns a copy of the registered bytes for a region name, or null. */
    public byte[] bytes(String regionName) {
        byte[] bytes = textures.get(regionName);
        return bytes == null ? null : bytes.clone();
    }

    /** Whether {@code regionName} is registered. */
    public boolean contains(String regionName) {
        return textures.containsKey(regionName);
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
        if (seen.putIfAbsent(connectionId, Boolean.TRUE) != null) {
            return;
        }
        boolean sent = false;
        try {
            // A copy, so a sender that writes to it cannot change the texture other connections get.
            sent = sender.trySend(connectionId, regionName, bytes.clone());
        } finally {
            if (!sent) {
                // Not on the client: the next use streams it again.
                seen.remove(connectionId);
            }
        }
    }

    /**
     * Drops the delivery markers of one connection so its textures re-stream on next use. The
     * client drops every server texture when it disconnects: call this when the player leaves.
     */
    public void resetDelivery(String connectionId) {
        for (Map<String, Boolean> m : delivered.values()) {
            m.remove(connectionId);
        }
    }

    /**
     * Drops every delivery marker. The client drops its server textures whenever it loads a world,
     * so a map change on the server leaves every player without them: call this then.
     */
    public void resetAllDeliveries() {
        delivered.clear();
    }

    /**
     * Forgets a texture: its bytes and its delivery markers. A later {@link #register} of the same
     * bytes brings it back under the same name. Clients that already have it keep it.
     *
     * @return whether the texture was registered
     */
    public boolean unregister(String regionName) {
        delivered.remove(regionName);
        return textures.remove(regionName) != null;
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
