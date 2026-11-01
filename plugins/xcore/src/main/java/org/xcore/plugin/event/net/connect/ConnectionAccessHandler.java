package org.xcore.plugin.event.net.connect;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressService;

@Singleton
public class ConnectionAccessHandler {

    private final IngressService ingressService;

    @Inject
    public ConnectionAccessHandler(IngressService ingressService) {
        this.ingressService = ingressService;
    }

    public boolean allow(NetConnection con, Packets.ConnectPacket packet) {
        AccessResult result = ingressService.validate(con, packet);

        if (result instanceof AccessResult.Denied(String reason, boolean silent, long kickDuration)) {
            if (silent) {
                con.close();
            } else {
                con.kick(reason, kickDuration);
            }
            return false;
        }

        return true;
    }
}
