// src/main/java/org/xcore/plugin/security/ingress/checks/WhitelistCheck.java
package org.xcore.plugin.security.ingress.checks;

import arc.util.Log;
import jakarta.inject.Singleton;
import mindustry.net.Administration;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;

import static mindustry.Vars.netServer;

/**
 * Enforces whitelist if enabled.
 * Priority -40: Fast admin lookup, synchronous.
 */
@Singleton
public class WhitelistCheck implements IngressCheck {

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        if (netServer.admins.isWhitelisted(packet.uuid, packet.usid)) {
            return AccessResult.Allowed.INSTANCE;
        }

        Administration.PlayerInfo info = netServer.admins.getInfo(packet.uuid);
        info.adminUsid = packet.usid;
        info.lastName = packet.name;
        info.id = packet.uuid;
        netServer.admins.save();

        Log.info("&lcDo &lywhitelist-add @&lc to whitelist the player &lb'@'",
                packet.uuid, packet.name);

        return new AccessResult.Denied(Packets.KickReason.whitelist.name(), false, 0);
    }

    @Override
    public int priority() {
        return -40;
    }

    @Override
    public String name() {
        return "WhitelistCheck";
    }
}
