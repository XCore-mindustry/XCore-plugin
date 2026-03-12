package org.xcore.plugin.event.net.connect;

import arc.Events;
import arc.util.Time;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import mindustry.net.NetConnection;
import mindustry.net.Packets;

@Singleton
public class ConnectPacketHandler {

    private final ConnectionAccessHandler connectionAccessHandler;
    private final PlayerConnectionBootstrap playerConnectionBootstrap;

    @Inject
    public ConnectPacketHandler(ConnectionAccessHandler connectionAccessHandler,
                                PlayerConnectionBootstrap playerConnectionBootstrap) {
        this.connectionAccessHandler = connectionAccessHandler;
        this.playerConnectionBootstrap = playerConnectionBootstrap;
    }

    public void handle(NetConnection con, Packets.ConnectPacket packet) {
        if (con.kicked) {
            return;
        }

        Events.fire(new EventType.ConnectPacketEvent(con, packet));
        con.connectTime = Time.millis();

        if (!connectionAccessHandler.allow(con, packet)) {
            return;
        }

        playerConnectionBootstrap.bootstrap(con, packet);
    }
}
