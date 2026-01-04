package org.xcore.plugin.listeners;

import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import mindustry.ui.Menus;
import mindustry.game.Rules;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.game.EventType.PlayerLeave;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.JsonIO;
import mindustry.maps.Map;
import mindustry.net.Administration;
import mindustry.net.Packets;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.models.AdminData;
import org.xcore.plugin.utils.models.PlayerData;
import org.xcore.plugin.utils.models.MapData;

import java.util.concurrent.atomic.AtomicInteger;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.*;

public class PluginEvents {
    private static int mapVoteMenuId;

    public static void init() {
        mapVoteMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1) return;

            var map = state.map;
            if (map == null) return;

            String mapName = map.plainName();
            PlayerData pData = database.getCached(player.uuid());

            MapData mData = database.mapDatas.get(mapName, map.author(), state.rules.mode().name());
            String mapIdStr = String.valueOf(mData.id);

            Boolean previousVote = pData.mapVotes.get(mapIdStr);

            if (selection == 0) {
                if (Boolean.TRUE.equals(previousVote)) return;
                if (previousVote == null) {
                    mData.reputation += 1; mData.popularity += 2.0;
                    bundle.send(player, "commands-like-success", args());
                } else {
                    mData.reputation += 2; mData.popularity += 4.0;
                    bundle.send(player, "commands-like-changed", args());
                }
                pData.mapVotes.put(mapIdStr, true);
            } else if (selection == 1) {
                if (Boolean.FALSE.equals(previousVote)) return;
                if (previousVote == null) {
                    mData.reputation -= 1; mData.popularity -= 2.0;
                    bundle.send(player, "commands-dislike-success", args());
                } else {
                    mData.reputation -= 2; mData.popularity -= 4.0;
                    bundle.send(player, "commands-dislike-changed", args());
                }
                pData.mapVotes.put(mapIdStr, false);
            }

            pData.save();
            mData.save();
        });

        Events.on(PlayerJoin.class, event -> {
            var player = event.player;

            Time.runTask(30, () -> {
                if (player.con.lastReceivedClientSnapshot == -1) {
                    player.kick("Maybe you are a bot. If not, try to reconnect.");
                }
            });

            bundle.send(player, "welcome", args("serverName", Administration.Config.serverName.string()));
            PlayerData data = database.getPlayerDatas().get(player);

            if (data == null) data = new PlayerData(player.uuid(), false);

            data.setNickname(player.coloredName()).setPlayer(player);

            Call.clientPacketReliable(player.con, "adm_mod_begin", "");

            if (data.exists && !data.ip.equals(player.ip())) {
                if (player.admin) {
                    AdminData adminData = data.adminData();
                    adminData.adminConfirmed = false;
                    adminData.save();

                    player.admin = false;
                    netServer.admins.unAdminPlayer(player.uuid());
                    bundle.send(player, "error-ip-changed", args());
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

            Log.info("@ #@ @ joined", player.plainName(), data.pid, player.uuid());
            bundle.send("player-joined", args(
                    "nickname", player.coloredName(),
                    "pid", data.pid));
            NetSock.post(new SocketEvents.PlayerJoinLeaveEvent(
                player.plainName() + " #" + data.pid,
                config.server,
                true)
            );
        });
        Events.on(PlayerLeave.class, event -> {
            Player player = event.player;

            var data = database.removeCached(event.player.uuid());

            if (vote != null) vote.left(event.player);

            if (voteKick != null) voteKick.left(event.player);

            Log.info("@ #@ @ left", player.plainName(), data.pid, player.uuid());

            bundle.send("player-left", args(
                "nickname", player.coloredName(),
                "pid", data.pid)
            );

            NetSock.post(new SocketEvents.PlayerJoinLeaveEvent(
                player.plainName() + " #" + data.pid,
                config.server,
                false)
            );
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
            Map nextMap = maps.getNextMap(state.rules.mode(), state.map);
            String nextName = (nextMap != null) ? nextMap.plainName() : "Unknown";
            String nextAuthor = (nextMap != null) ? nextMap.author() : "Unknown";

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

            NetSock.post(new SocketEvents.ServerActionEvent(message, config.server));

            if (state.map != null && !state.isMenu()) {
                try {
                    String mapName = state.map.plainName();
                    String author = state.map.author();
                    String modeName = state.rules.mode().name();

                    long durationMillis = (long) ((state.tick / 60f) * 1000f);

                    if (durationMillis > 120 * 1000) {

                        MapData stats = database.getMapDatas().get(mapName, author, modeName);
                        boolean isWin = event.winner != null && event.winner != state.rules.waveTeam;

                        stats.registerGame(durationMillis, isWin, modeName, author);
                        stats.save();

                        Log.info("Map stats updated for '@'", mapName);
                    }
                } catch (Exception e) {
                    Log.err("Failed to update map stats", e);
                }
            }

            Groups.player.each(p -> {
                String menuTitle = bundle.format(bundle.locale(p), "map-vote-title", args());

                String menuContent = bundle.format(bundle.locale(p), "map-vote-content", args(
                    "mapName", nextName,
                    "author", nextAuthor,
                    "seconds", 10
                ));

                Call.menu(p.con, mapVoteMenuId, menuTitle, menuContent, new String[][]{
                    {
                        bundle.format(bundle.locale(p), "map-vote-like", args()),
                        bundle.format(bundle.locale(p), "map-vote-dislike", args())
                    },
                    {
                        bundle.format(bundle.locale(p), "map-vote-close", args())
                    }
                });
            });

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
