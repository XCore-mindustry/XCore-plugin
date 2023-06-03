package org.xcore.plugin.listeners;

import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.game.EventType;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.utils.SockCommunicator;
import useful.Bundle;

import static mindustry.Vars.*;
import static mindustry.Vars.player;
import static org.xcore.plugin.PluginVars.*;
import static useful.Bundle.send;

public class PluginEvents {
    public static void init() {
        Events.on(EventType.PlayerConnect.class, event -> {
            var player = event.player;
            var info = netServer.admins.getInfo(event.player.uuid());

            Call.clientPacketReliable(event.player.con, "adm_mod_begin", "");

            if (!info.ips.contains(event.player.con.address)) {
                event.player.admin = false;
                netServer.admins.unAdminPlayer(event.player.uuid());
            }

            var data = database.getPlayerDataExecutor().getPlayerData(player).setNickname(player.coloredName());
            if (!data.exists) {
                data.generatePid();
                database.getPlayerDataExecutor().setPlayerData(data);
            }

            HexedRanks.updateRank(player, data);
            database.setCached(data);
        });
        Events.on(PlayerJoin.class, event -> {
            var player = event.player;

            Time.runTask(30, () -> {
                if (player.con.lastReceivedClientSnapshot == -1) {
                    player.kick("VPN services forbidden!");
                }
            });

            var data = database.getCached(event.player.uuid());

            if (player.getInfo().timesJoined < 5)
                Call.openURI(player.con, discordUrl);

            if (data.translatorLanguage.equals("off")) {
                send(event.player, "recommendation.tr");
            }

            Log.info("@ (@/@) joined", player.plainName(), data.pid, player.uuid());
            Bundle.send("player.joined", player.coloredName(), data.pid);
            SockCommunicator.sendEvent(
                    new SocketEvents.PlayerJoinLeaveEvent(player.plainName() + " (" + data.pid + ")", config.server, true));
        });
        Events.on(PlayerLeave.class, event -> {
            Player player = event.player;

            var data = database.removeCached(event.player.uuid());

            if (vote != null) vote.left(event.player);
            if (voteKick != null) voteKick.left(event.player);

            Log.info("@ (@/@) left", player.plainName(), data.pid, player.uuid());
            Bundle.send("player.left", player.coloredName(), data.pid);
            SockCommunicator.sendEvent(
                    new SocketEvents.PlayerJoinLeaveEvent(player.plainName() + " (" + data.pid + ")", config.server, false));
        });

        Events.on(GameOverEvent.class, event -> {
            String message = "Game over!";

            if (state.rules.waves) {
                message = Strings.format(
                        "Game over! Reached wave @ with @ players online on map @.", state.wave, Groups.player.size(), Strings.capitalize(Strings.stripColors(state.map.name())));
            } else if (state.rules.pvp && !config.isMiniHexed()) {
                message = Strings.format(
                        "Game over! Team @ is victorious with @ players online on map @.", event.winner.name, Groups.player.size(), Strings.capitalize(Strings.stripColors(state.map.name())));
            }

            SockCommunicator.sendEvent(
                    new SocketEvents.ServerActionEvent(message, config.server));
        });
    }
}
