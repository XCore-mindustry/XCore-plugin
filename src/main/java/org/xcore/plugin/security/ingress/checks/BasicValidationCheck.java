package org.xcore.plugin.security.ingress.checks;

import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;

/**
 * Basic packet validation (UUID, USID, connection state).
 * Priority -110: First check, ensures packet integrity.
 */
@Singleton
public class BasicValidationCheck implements IngressCheck {

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        if (con.kicked) {
            return new AccessResult.Denied("Already kicked", true, 0);
        }

        if (packet.uuid == null || packet.usid == null) {
            return new AccessResult.Denied(Packets.KickReason.idInUse.name(), false, 0);
        }

        if (con.hasBegunConnecting) {
            return new AccessResult.Denied(Packets.KickReason.idInUse.name(), false, 0);
        }

        if (packet.locale == null) {
            packet.locale = "en";
        }

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return -110;
    }

    @Override
    public String name() {
        return "BasicValidationCheck";
    }
}