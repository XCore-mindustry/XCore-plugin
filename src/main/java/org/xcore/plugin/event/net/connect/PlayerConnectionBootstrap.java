package org.xcore.plugin.event.net.connect;

import arc.Events;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.session.ObserverService;

import static mindustry.Vars.netServer;

@Singleton
public class PlayerConnectionBootstrap {

    private final ObserverService observerService;

    @Inject
    public PlayerConnectionBootstrap(ObserverService observerService) {
        this.observerService = observerService;
    }

    public PlayerConnectionBootstrap() {
        this(null);
    }

    public void bootstrap(NetConnection con, Packets.ConnectPacket packet) {
        String uuid = packet.uuid;
        Administration.PlayerInfo info = netServer.admins.getInfo(uuid);

        netServer.admins.updatePlayerJoined(uuid, con.address, packet.name);

        Player player = Player.create();
        player.admin = netServer.admins.isAdmin(uuid, packet.usid);
        player.con = con;
        player.con.usid = packet.usid;
        player.con.uuid = uuid;
        player.con.mobile = packet.mobile;
        player.name = packet.name;
        player.locale = packet.locale;
        player.color.set(packet.color).a(1f);

        if (!player.admin && !info.admin) {
            info.adminUsid = packet.usid;
        }

        con.player = player;
        player.team(netServer.assignTeam(player));
        if (observerService != null) {
            observerService.restore(player);
        }

        netServer.sendWorldData(player);

        Events.fire(new EventType.PlayerConnect(player));
    }
}
