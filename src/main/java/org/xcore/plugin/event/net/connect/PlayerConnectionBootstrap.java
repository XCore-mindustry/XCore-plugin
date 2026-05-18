package org.xcore.plugin.event.net.connect;

import arc.Events;
import arc.struct.Seq;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import mindustry.logic.LVar;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import mindustry.world.Tile;
import mindustry.world.blocks.logic.LogicBlock;
import org.xcore.plugin.session.ObserverService;

import static mindustry.Vars.netServer;
import static mindustry.Vars.world;

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

        sendWorldData(player);

        Events.fire(new EventType.PlayerConnect(player));
    }

    private void sendWorldData(Player player) {
        try {
            netServer.sendWorldData(player);
        } catch (IllegalArgumentException ex) {
            if (!isUnknownSeqType(ex)) {
                throw ex;
            }

            int sanitized = sanitizeLogicSeqVariables();
            if (sanitized <= 0) {
                throw ex;
            }

            Log.warn("Sanitized @ logic variables with Seq values before retrying world send for @ (@)",
                    sanitized, player.plainName(), player.uuid());
            netServer.sendWorldData(player);
        }
    }

    private boolean isUnknownSeqType(IllegalArgumentException ex) {
        return ex.getMessage() != null && ex.getMessage().contains("Unknown object type: class arc.struct.Seq");
    }

    static int sanitizeLogicSeqVariables() {
        if (world == null || world.tiles == null) {
            return 0;
        }

        int sanitized = 0;

        for (Tile tile : world.tiles) {
            if (tile != null && tile.build instanceof LogicBlock.LogicBuild logicBuild) {
                sanitized += sanitizeLogicSeqVariables(logicBuild.executor.vars);
            }
        }

        return sanitized;
    }

    private static int sanitizeLogicSeqVariables(LVar[] vars) {
        int sanitized = 0;

        for (LVar var : vars) {
            if (var != null && var.isobj && var.objval instanceof Seq<?>) {
                var.objval = null;
                sanitized++;
            }
        }

        return sanitized;
    }
}
