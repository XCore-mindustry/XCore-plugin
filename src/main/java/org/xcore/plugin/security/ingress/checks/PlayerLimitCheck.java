package org.xcore.plugin.security.ingress.checks;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;

import static mindustry.Vars.netServer;

/**
 * Enforces player limit (admins bypass).
 * Priority -50: Fast count check, synchronous.
 */
@Singleton
public class PlayerLimitCheck implements IngressCheck {

    private final Config config;

    @Inject
    public PlayerLimitCheck(Config config) {
        this.config = config;
    }

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        if (config.playerLimit <= 0) {
            return AccessResult.Allowed.INSTANCE;
        }

        boolean isAdmin = netServer.admins.isAdmin(packet.uuid, packet.usid);
        if (isAdmin) {
            return AccessResult.Allowed.INSTANCE;
        }

        if (Groups.player.size() >= config.getNoAdminPlayerLimit()) {
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
}