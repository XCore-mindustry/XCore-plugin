package org.xcore.plugin.security.ingress.checks;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;

import static mindustry.Vars.netServer;

/**
 * Enforces player limit (admins bypass).
 * Priority -50: Fast count check, synchronous.
 */
@Singleton
public class PlayerLimitCheck implements IngressCheck {

    private final TomlXcoreConfig config;

    @Inject
    public PlayerLimitCheck(TomlXcoreConfig config) {
        this.config = config;
    }

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        if (config.server.playerLimit <= 0) {
            return AccessResult.Allowed.INSTANCE;
        }

        boolean isAdmin = netServer.admins.isAdmin(packet.uuid, packet.usid);
        if (isAdmin) {
            return AccessResult.Allowed.INSTANCE;
        }

        if (Groups.player.size() >= noAdminPlayerLimit()) {
            return new AccessResult.Denied(Packets.KickReason.playerLimit.name(), false, 0);
        }

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return -50;
    }

    @Override
    public String name() {
        return "PlayerLimitCheck";
    }

    private int noAdminPlayerLimit() {
        return config.server.playerLimit + Groups.player.count(player -> player.admin);
    }
}
