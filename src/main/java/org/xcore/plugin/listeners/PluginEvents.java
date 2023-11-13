package org.xcore.plugin.listeners;

import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import mindustry.game.Rules;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.JsonIO;
import mindustry.net.Administration;
import mindustry.net.Packets;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.utils.NetSock;
import useful.Bundle;

import java.util.concurrent.atomic.AtomicInteger;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.*;
import static useful.Bundle.send;

public class PluginEvents {
    public static void init() {
        Events.on(PlayerJoin.class, event -> {
            var player = event.player;

            Time.runTask(30, () -> {
                if (player.con.lastReceivedClientSnapshot == -1) {
                    player.kick("VPN services forbidden!");
                }
            });

            send(player, "welcome", Administration.Config.serverName.string());
            var data = database.getPlayerDatas().getPlayerData(player)
                    .setNickname(player.coloredName())
                    .setPlayer(player);

            Call.clientPacketReliable(player.con, "adm_mod_begin", "");

            if (data.exists && !data.ip.equals(player.ip())) {
                if (player.admin) {
                    data.adminConfirmed = false;
                    player.admin = false;
                    netServer.admins.unAdminPlayer(player.uuid());
                    send(player, "error.ip-changed");
                }

                data.setIp(player.ip());
                data.save();
            }

            if (!data.exists) {
                data.generatePid();
                data.setIp(player.ip());
                data.save();
            }

            HexedRanks.updateRank(player, data);
            database.setCached(data);

            if (player.getInfo().timesJoined < 5)
                Call.openURI(player.con, discordUrl);

            Log.info("@ (@/@) joined", player.plainName(), data.pid, player.uuid());
            Bundle.send("player.joined", player.coloredName(), data.pid);
            NetSock.post(
                    new SocketEvents.PlayerJoinLeaveEvent(player.plainName() + " (" + data.pid + ")", config.server,
                            true));
        });
        Events.on(PlayerLeave.class, event -> {
            Player player = event.player;

            var data = database.removeCached(event.player.uuid());

            if (vote != null)
                vote.left(event.player);
            if (voteKick != null)
                voteKick.left(event.player);

            Log.info("@ (@/@) left", player.plainName(), data.pid, player.uuid());
            Bundle.send("player.left", player.coloredName(), data.pid);
            NetSock.post(
                    new SocketEvents.PlayerJoinLeaveEvent(player.plainName() + " (" + data.pid + ")", config.server,
                            false));
        });

        Events.on(PlayEvent.class, event -> {
            gameStarted = Time.millis();

            var mapRules = JsonIO.read(Rules.class, state.map.tags.get("rules"));
            state.rules.bannedBlocks.addAll(mapRules.bannedBlocks);
            state.rules.bannedUnits.addAll(mapRules.bannedUnits);
            state.rules.revealedBlocks.addAll(mapRules.revealedBlocks);
            Log.info("@ banned blocks, @ banned units, @ revealed blocks", mapRules.bannedBlocks.size, mapRules.bannedUnits.size, mapRules.revealedBlocks.size);
        });

        Events.on(String.class, event -> {
            if ((event.equals("rvsb_world-reload") || event.equals("hexed_world-reload")) && gameoverRestart) restart();
        });

        Events.on(GameOverEvent.class, event -> {
            String message = "Game over!";

            if (state.rules.waves) {
                message = Strings.format(
                        "Game over! Reached wave @ with @ players online on map @.", state.wave, Groups.player.size(),
                        Strings.capitalize(Strings.stripColors(state.map.name())));
            } else if (state.rules.pvp && !config.isMiniHexed()) {
                message = Strings.format(
                        "Game over! Team @ is victorious with @ players online on map @.", event.winner.name,
                        Groups.player.size(), Strings.capitalize(Strings.stripColors(state.map.name())));
            }

            NetSock.post(
                    new SocketEvents.ServerActionEvent(message, config.server));

            if (gameoverRestart) restart();
        });
    }

    private static void restart() {
        AtomicInteger secondsLeft = new AtomicInteger(10);
        Timer.schedule(() -> {
            Call.announce("Restart in " + secondsLeft.get());
            if (secondsLeft.decrementAndGet() == 0) {
                netServer.kickAll(Packets.KickReason.serverRestarting);
                System.exit(0);
            }
        }, 0, 1);
    }
}
