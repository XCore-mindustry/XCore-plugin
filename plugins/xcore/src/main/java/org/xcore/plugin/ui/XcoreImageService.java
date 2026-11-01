package org.xcore.plugin.ui;

import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.event.EventSubscriptions;
import org.xcore.plugin.startup.ShutdownOrder;
import org.xcore.ui.texture.TextureRegistry;

import java.util.Objects;

/**
 * Server-side texture streaming service integrating {@link TextureRegistry}
 * with Mindustry v160's {@link mindustry.core.NetServer#sendTexture}.
 */
@Singleton
public class XcoreImageService {

    private final TextureRegistry registry;
    private final EventSubscriptions subscriptions = new EventSubscriptions();

    public XcoreImageService() {
        this((connectionId, regionName, bytes) -> {
            Player player = Groups.player != null ? Groups.player.find(p -> Objects.equals(p.uuid(), connectionId)) : null;
            if (player != null && player.con != null && Vars.netServer != null) {
                String name = regionName.startsWith(TextureRegistry.NET_PREFIX)
                        ? regionName.substring(TextureRegistry.NET_PREFIX.length())
                        : regionName;
                Vars.netServer.sendTexture(player.con, name, bytes);
            }
        });
    }

    XcoreImageService(TextureRegistry.TextureSender sender) {
        this.registry = new TextureRegistry(sender);
    }

    /**
     * The client drops every texture the server streamed when it disconnects and whenever it loads
     * a world, so what the registry counts as delivered has to be forgotten then, or the dialogs
     * opened after a map change show placeholders until the server restarts.
     */
    @PostConstruct
    public void init() {
        subscriptions.on(EventType.PlayerLeave.class, event -> reset(event.player));
        subscriptions.on(EventType.WorldLoadEvent.class, event -> onWorldLoad());
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        subscriptions.close();
    }

    /** Every client reloads the world, and with it drops the textures it was sent. */
    void onWorldLoad() {
        registry.resetAllDeliveries();
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

    /** Forgets a texture no dialog will show again, such as one a cache evicted. */
    public void unregister(String regionName) {
        if (regionName != null) {
            registry.unregister(regionName);
        }
    }

    /** Drops cached delivery state when a player disconnects or reconnects. */
    public void reset(Player player) {
        if (player == null) return;
        registry.resetDelivery(player.uuid());
    }
}
