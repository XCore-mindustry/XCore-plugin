package org.xcore.plugin.security.ingress.checks;

import jakarta.inject.Singleton;
import mindustry.core.Version;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;

import static mindustry.Vars.netServer;

/**
 * Validates client version compatibility.
 * Priority -100: Very fast, runs first synchronously.
 */
@Singleton
public class VersionCheck implements IngressCheck {

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        if (packet.versionType == null ||
                ((packet.version == -1 || !packet.versionType.equals(Version.type))
                        && Version.build != -1
                        && !netServer.admins.allowsCustomClients())) {

            String reason = !Version.type.equals(packet.versionType)
                    ? Packets.KickReason.typeMismatch.name()
                    : Packets.KickReason.customClient.name();
            return new AccessResult.Denied(reason, false, 0);
        }

        if (packet.version != Version.build && Version.build != -1 && packet.version != -1) {
            String reason = packet.version > Version.build
                    ? Packets.KickReason.serverOutdated.name()
                    : Packets.KickReason.clientOutdated.name();
            return new AccessResult.Denied(reason, false, 0);
        }

        if (packet.version == -1) {
            con.modclient = true;
        }

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return -100;
    }

    @Override
    public String name() {
        return "VersionCheck";
    }
}
