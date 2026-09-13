package org.xcore.plugin.ui;

import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.ui.texture.TextureRegistry;

import java.util.Objects;

/**
 * Server-side texture streaming service integrating {@link TextureRegistry}
 * with Mindustry v160's {@link mindustry.core.NetServer#sendTexture}.
 */
@Singleton
public class XcoreImageService {

    private final TextureRegistry registry;

    public XcoreImageService() {
        this.registry = new TextureRegistry((connectionId, regionName, bytes) -> {
            Player player = Groups.player != null ? Groups.player.find(p -> Objects.equals(p.uuid(), connectionId)) : null;
            if (player != null && player.con != null && Vars.netServer != null) {
                String name = regionName.startsWith(TextureRegistry.NET_PREFIX)
                        ? regionName.substring(TextureRegistry.NET_PREFIX.length())
                        : regionName;
                Vars.netServer.sendTexture(player.con, name, bytes);
            }
        });
    }

    public TextureRegistry registry() {
        return registry;
    }

    /** Registers a PNG byte array into the content-addressed registry and returns the {@code net-} region. */
    public String register(byte[] pngBytes) {
        return registry.register(pngBytes);
    }

    /**
     * Ensures the given player has received the texture over the network stream.
     * Guaranteed to send bytes at most once per session per texture.
     */
    public String ensureDelivered(Player player, byte[] pngBytes) {
        if (player == null) return null;
        String regionName = registry.register(pngBytes);
        ensureDeliveredName(player, regionName);
        return regionName;
    }

    /** Ensures a pre-registered texture by its {@code net-} region name is streamed to the player. */
    public void ensureDeliveredName(Player player, String regionName) {
        if (player == null) return;
        byte[] bytes = registry.bytes(regionName);
        if (bytes != null) {
            registry.ensureDeliveredName(player.uuid(), regionName);
        }
    }

    /** Drops cached delivery state when a player disconnects or reconnects. */
    public void reset(Player player) {
        if (player == null) return;
        registry.resetDelivery(player.uuid());
    }
}
