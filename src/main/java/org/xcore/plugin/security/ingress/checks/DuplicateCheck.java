package org.xcore.plugin.security.ingress.checks;

import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;

import static arc.util.Strings.stripColors;
import static mindustry.Vars.net;
import static mindustry.Vars.netServer;

/**
 * Prevents duplicate connections (same UUID, USID, or name).
 * Priority -70: Fast in-memory check, synchronous.
 */
@Singleton
public class DuplicateCheck implements IngressCheck {

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        if (!netServer.admins.isStrict()) {
            return AccessResult.Allowed.INSTANCE;
        }

        String uuid = packet.uuid;
        String usid = packet.usid;
        String name = packet.name;

        boolean nameDuplicate = Groups.player.contains(p ->
                stripColors(p.name).trim().equalsIgnoreCase(stripColors(name).trim()));

        if (nameDuplicate) {
            return new AccessResult.Denied(Packets.KickReason.nameInUse.name(), false, 0);
        }

        boolean idDuplicate = Groups.player.contains(p ->
                p.uuid().equals(uuid) || p.usid().equals(usid));

        if (idDuplicate) {
            con.uuid = uuid;
            return new AccessResult.Denied(Packets.KickReason.idInUse.name(), false, 0);
        }

        for (var otherCon : net.getConnections()) {
            if (otherCon != con && uuid.equals(otherCon.uuid)) {
                con.uuid = uuid;
                return new AccessResult.Denied(Packets.KickReason.idInUse.name(), false, 0);
            }
        }

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return -70;
    }

    @Override
    public String name() {
        return "DuplicateCheck";
    }
}
