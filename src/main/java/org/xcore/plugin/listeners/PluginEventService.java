package org.xcore.plugin.listeners;

import arc.Events;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.core.GameState;
import mindustry.game.EventType.*;
import mindustry.game.Rules;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.io.JsonIO;
import mindustry.maps.Map;
import mindustry.net.Packets;
import mindustry.server.ServerControl;
import mindustry.ui.Menus;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.bundles.BundleService;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.modules.hexed.HexedRanks;
import org.xcore.plugin.modules.network.NetworkService;
import org.xcore.plugin.modules.votes.VoteService;
import org.xcore.plugin.utils.models.MapData;
import org.xcore.plugin.utils.models.PlayerData;

import java.util.concurrent.atomic.AtomicInteger;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.*;

@Singleton
public class PluginEventService {

    private int mapVoteMenuId;

    private final DatabaseService database;
    private final NetworkService network;
    private final Config config;
    private final BundleService bundleService;
    private final VoteService voteService;

    @Inject
    public PluginEventService(DatabaseService database, NetworkService network, Config config,
                              BundleService bundleService, VoteService voteService) {
        this.database = database;
        this.network = network;
        this.config = config;
        this.bundleService = bundleService;
        this.voteService = voteService;
    }

    @PostConstruct
    public void init() {
        mapVoteMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1) return;

            var map = state.map;
            if (map == null) return;

            String mapName = map.plainName();
            PlayerData pData = database.getCached(player.uuid());

            MapData mData = database.getMapDataRepository().find(mapName, map.author(), state.rules.mode().name());
            String mapIdStr = String.valueOf(mData.id);

            Boolean previousVote = pData.mapVotes.get(mapIdStr);

            if (selection == 0) {
                if (Boolean.TRUE.equals(previousVote)) {
                    bundleService.send(player, "error-already-voted", args());
                    return;
                }

                if (previousVote == null) {
                    mData.reputation += 1;
                    mData.popularity += 2.0;
                    bundleService.send(player, "commands-like-success", args());
                } else {
                    mData.reputation += 2;
                    mData.popularity += 4.0;
                    bundleService.send(player, "commands-like-changed", args());
                }
                pData.mapVotes.put(mapIdStr, true);
            } else if (selection == 1) {
                if (Boolean.FALSE.equals(previousVote)) {
                    bundleService.send(player, "error-already-voted", args());
                    return;
                }

                if (previousVote == null) {
                    mData.reputation -= 1;
                    mData.popularity -= 2.0;
                    bundleService.send(player, "commands-dislike-success", args());
                } else {
                    mData.reputation -= 2;
                    mData.popularity -= 4.0;
                    bundleService.send(player, "commands-dislike-changed", args());
                }
                pData.mapVotes.put(mapIdStr, false);
            }

            database.getPlayerDataRepository().save(pData);
            database.getMapDataRepository().save(mData);
        });

        Events.on(PlayerJoin.class, event -> {
            var player = event.player;

            Time.runTask(120, () -> {
                if (player != null && player.con != null && player.con.isConnected()) {
                    if (player.con.lastReceivedClientSnapshot == -1) {
                        player.kick("Maybe you are a bot. If not, try to reconnect.");
                    }
                }
            });

            bundleService.send(player, "welcome", args("serverName", mindustry.net.Administration.Config.serverName.string()));
            PlayerData data = database.getPlayerDataRepository().findByPlayer(player);

            if (data == null) data = new PlayerData(player.uuid(), false);

            data.setNickname(player.coloredName()).setPlayer(player);

            Call.clientPacketReliable(player.con, "adm_mod_begin", "");

            if (data.exists && !data.ip.equals(player.ip())) {
                if (player.admin) {
                    var adminData = database.getAdminDataRepository().findByUuid(data.uuid);
                    adminData.adminConfirmed = false;
                    database.getAdminDataRepository().save(adminData);

                    player.admin = false;
                    netServer.admins.unAdminPlayer(player.uuid());
                    bundleService.send(player, "error-ip-changed", args());
                }

                data.setIp(player.ip());
                database.getPlayerDataRepository().save(data);
            }

            if (!data.exists) {
                // data.generatePid(); now, automatically generated
                data.setIp(player.ip());
                database.getPlayerDataRepository().save(data);
            }

            HexedRanks.updateRank(player, data, config);
            database.setCached(data);

            if (player.getInfo().timesJoined < 5) {
                Call.openURI(player.con, discordUrl);
            }

            Log.info("@ #@ @ joined", player.plainName(), data.pid, player.uuid());
            bundleService.send("player-joined", args(
                    "nickname", player.coloredName(),
                    "pid", data.pid));
            network.post(new SocketEvents.PlayerJoinLeaveEvent(
                    player.plainName() + " #" + data.pid,
                    config.server,
                    true)
            );
        });

        Events.on(PlayerLeave.class, event -> {
            Player player = event.player;

            var data = database.removeCached(event.player.uuid());

            voteService.handleLeave(event.player);

            if (data != null) {
                Log.info("@ #@ @ left", player.plainName(), data.pid, player.uuid());
                bundleService.send("player-left", args(
                        "nickname", player.coloredName(),
                        "pid", data.pid)
                );

                network.post(new SocketEvents.PlayerJoinLeaveEvent(
                        player.plainName() + " #" + data.pid,
                        config.server,
                        false)
                );
            }
        });

        Events.on(PlayEvent.class, event -> {
            gameStarted = Time.millis();

            var mapRules = JsonIO.read(Rules.class, state.map.tags.get("rules"));
            if (mapRules != null) {
                state.rules.bannedBlocks.addAll(mapRules.bannedBlocks);
                state.rules.bannedUnits.addAll(mapRules.bannedUnits);
                state.rules.revealedBlocks.addAll(mapRules.revealedBlocks);
                Log.info("@ banned blocks, @ banned units, @ revealed blocks",
                        mapRules.bannedBlocks.size, mapRules.bannedUnits.size, mapRules.revealedBlocks.size);
            }
        });

        Events.on(String.class, event -> {
            if ((event.equals("rvsb_world-reload") || event.equals("hexed_world-reload")) && gameoverRestart) {
                restart();
            }
        });

        ServerControl.instance.gameOverListener = event -> {
            if (state.rules.waves) {
                Log.info("Game over! Reached wave @ with @ players online on map @.",
                        state.wave, Groups.player.size(), Strings.capitalize(state.map.plainName()));
            } else {
                Log.info("Game over! Team @ is victorious with @ players online on map @.",
                        event.winner.name, Groups.player.size(), Strings.capitalize(state.map.plainName()));
            }

            Map nextMap = maps.getNextMap(ServerControl.instance.lastMode, state.map);
            String nextName = (nextMap != null) ? nextMap.plainName() : "Unknown";
            String nextAuthor = (nextMap != null) ? nextMap.author() : "Unknown";

            if (nextMap != null) {
                state.gameOver = true;
                Call.updateGameOver(event.winner);

                Groups.player.each(p -> {
                    String menuTitle = bundleService.format(bundleService.locale(p), "map-vote-title", args());

                    String menuContent = bundleService.format(bundleService.locale(p), "map-vote-content", args(
                            "mapName", nextName,
                            "author", nextAuthor,
                            "seconds", 10
                    ));

                    PlayerData pData = database.getCached(p.uuid());
                    String mapIdStr = String.valueOf(database.getMapDataRepository()
                            .find(state.map.plainName(), state.map.author(), state.rules.mode().name()).id);
                    Boolean currentVote = pData.mapVotes.get(mapIdStr);

                    String likeBtn = Boolean.TRUE.equals(currentVote)
                            ? bundleService.format(bundleService.locale(p), "map-vote-like-selected", args())
                            : bundleService.format(bundleService.locale(p), "map-vote-like", args());
                    String dislikeBtn = Boolean.FALSE.equals(currentVote)
                            ? bundleService.format(bundleService.locale(p), "map-vote-dislike-selected", args())
                            : bundleService.format(bundleService.locale(p), "map-vote-dislike", args());

                    Call.menu(p.con, mapVoteMenuId, menuTitle, menuContent, new String[][]{
                            {likeBtn, dislikeBtn},
                            {bundleService.format(bundleService.locale(p), "close", args())}
                    });
                });

                Map finalNextMap = nextMap;
                ServerControl.instance.play(() -> world.loadMap(finalNextMap,
                        finalNextMap.applyRules(ServerControl.instance.lastMode)));
            } else {
                netServer.kickAll(Packets.KickReason.gameover);
                state.set(GameState.State.menu);
                net.closeServer();
            }
        };

        Events.on(GameOverEvent.class, event -> {
            String message = "Game over!";

            if (state.rules.waves) {
                message = Strings.format(
                        "Game over! Reached wave @ with @ players online on map @.",
                        state.wave, Groups.player.size(),
                        Strings.capitalize(Strings.stripColors(state.map.name())));
            } else if (state.rules.pvp && !config.isMiniHexed()) {
                message = Strings.format(
                        "Game over! Team @ is victorious with @ players online on map @.",
                        event.winner.name, Groups.player.size(),
                        Strings.capitalize(Strings.stripColors(state.map.name())));
            }

            network.post(new SocketEvents.ServerActionEvent(message, config.server));

            if (state.map != null && !state.isMenu()) {
                try {
                    String mapName = state.map.plainName();
                    String author = state.map.author();
                    String modeName = state.rules.mode().name();

                    long durationMillis = (long) ((state.tick / 60f) * 1000f);

                    if (durationMillis > 120 * 1000) {
                        MapData stats = database.getMapDataRepository().find(mapName, author, modeName);
                        boolean isWin = event.winner != null && event.winner != state.rules.waveTeam;

                        stats.registerGame(durationMillis, isWin, modeName, author);
                        database.getMapDataRepository().save(stats);

                        Log.info("Map stats updated for '@'", mapName);
                    }
                } catch (Exception e) {
                    Log.err("Failed to update map stats", e);
                }
            }

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