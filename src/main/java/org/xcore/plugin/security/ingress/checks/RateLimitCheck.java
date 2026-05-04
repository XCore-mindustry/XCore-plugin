package org.xcore.plugin.security.ingress.checks;

import arc.struct.Seq;
import jakarta.inject.Singleton;
import mindustry.net.NetConnection;
import mindustry.net.Packets.ConnectPacket;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressCheck;

import static mindustry.Vars.net;
import static mindustry.Vars.netServer;

/**
 * Rate limits connections from the same IP address.
 * Priority -80: Very fast, synchronous.
 */
@Singleton
public class RateLimitCheck implements IngressCheck {

    private static final int MAX_CONNECTIONS_PER_IP = 3;

    @Override
    public AccessResult check(NetConnection con, ConnectPacket packet) {
        var connections = Seq.with(net.getConnections())
                .select(connection -> java.util.Objects.equals(connection.address, con.address));

        if (connections.size >= MAX_CONNECTIONS_PER_IP) {
            netServer.admins.blacklistDos(con.address);
            connections.each(NetConnection::close);
            return new AccessResult.Denied("Too many connections", true, 0);
        }

        return AccessResult.Allowed.INSTANCE;
    }

    @Override
    public int priority() {
        return -80;
    }

    @Override
    public String name() {
        return "RateLimitCheck";
    }
}
