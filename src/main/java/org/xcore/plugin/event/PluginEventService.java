package org.xcore.plugin.event;

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
import org.bson.types.ObjectId;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.database.repository.AdminDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;

@Singleton
public class PluginEventService {

    private int mapVoteMenuId;

    private final PlayerSessionService playerSessionService;
    private final PlayerDataRepository playerDataRepository;
    private final MapDataRepository mapDataRepository;
    private final AdminDataRepository adminDataRepository;
    private final NetworkService network;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final BundleService bundleService;
    private final VoteService voteService;
    private final PluginState pluginState;

    @Inject
    public PluginEventService(PlayerSessionService playerSessionService,
                              PlayerDataRepository playerDataRepository,
                              MapDataRepository mapDataRepository,
                              AdminDataRepository adminDataRepository,
                              NetworkService network,
                              Config config,
                              GlobalConfig globalConfig,
                              BundleService bundleService,
                              VoteService voteService,
                              PluginState pluginState) {
        this.playerSessionService = playerSessionService;
        this.playerDataRepository = playerDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.adminDataRepository = adminDataRepository;
        this.network = network;
        this.config = config;
        this.globalConfig = globalConfig;
        this.bundleService = bundleService;
        this.voteService = voteService;
        this.pluginState = pluginState;
    }

    @PostConstruct
    public void init() {
        mapVoteMenuId = Menus.registerMenu((player, selection) -> {
            if (selection == -1) return;

            var map = state.map;
            if (map == null) return;

            String mapName = map.plainName();
            PlayerData pData = playerSessionService.get(player.uuid());

            MapData mData = mapDataRepository.findOrCreate(mapName, map.file.name(), map.author(), state.rules.mode().name());

            Boolean previousVote = pData.mapVotes.get(mData.id);

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
                pData.mapVotes.put(mData.id, true);
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
                pData.mapVotes.put(mData.id, false);
            }

            playerDataRepository.save(pData);
            mapDataRepository.save(mData);
        });

        Events.on(PlayerJoin.class, event -> {
            var player = event.player;

            Time.runTask(120, () -> {
                if (player != null && player.con != null && player.con.isConnected()) {
                    if (player.con.lastReceivedClientSnapshot == -1) {
                        String kickMsg = bundleService.format(bundleService.locale(player), "kick-bot-protection", args());
                        player.kick(kickMsg);
                    }
                }
            });

            bundleService.send(player, "welcome", args("serverName", mindustry.net.Administration.Config.serverName.string()));
            PlayerData data = playerDataRepository.findByPlayer(player);

            if (data == null) data = new PlayerData(player.uuid(), false);

            data.setNickname(player.coloredName()).setPlayer(player);

            Call.clientPacketReliable(player.con, "adm_mod_begin", "");

            if (data.exists && !data.ip.equals(player.ip())) {
                if (player.admin) {
                    var adminData = adminDataRepository.findByUuid(data.uuid);
                    adminData.adminConfirmed = false;
                    adminDataRepository.save(adminData);

                    player.admin = false;
                    netServer.admins.unAdminPlayer(player.uuid());
                    bundleService.send(player, "error-ip-changed", args());
                }

                data.setIp(player.ip());
                playerDataRepository.save(data);
            }

            if (!data.exists) {
                // data.generatePid(); now, automatically generated
                data.setIp(player.ip());
                playerDataRepository.save(data);
            }

            HexedRanks.updateRank(player, data, config);
            playerSessionService.update(data);

            if (player.getInfo().timesJoined < 5) {
                Call.openURI(player.con, globalConfig.discordUrl);
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

            var data = playerSessionService.registerLogout(event.player.uuid());

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
            pluginState.gameStartTime = Time.millis();

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
            if ((event.equals("rvsb_world-reload") || event.equals("hexed_world-reload")) && pluginState.restartOnGameOver) {
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

                    PlayerData pData = playerSessionService.get(p.uuid());
                    ObjectId mapID = mapDataRepository.findOrCreate(state.map.plainName(), state.map.file.name(), state.map.author(), state.rules.mode().name()).id;
                    Boolean currentVote = pData.mapVotes.get(mapID);

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
                    String mapFileName = state.map.file.name();
                    String author = state.map.author();
                    String modeName = state.rules.mode().name();

                    long durationMillis = (long) ((state.tick / 60f) * 1000f);

                    if (durationMillis > 120 * 1000) {
                        MapData stats = mapDataRepository.findOrCreate(mapName, mapFileName, author, modeName);
                        boolean isWin = event.winner != null && event.winner != state.rules.waveTeam;

                        stats.registerGame(durationMillis, isWin, modeName, author);
                        mapDataRepository.save(stats);

                        Log.info("Map stats updated for '@'", mapName);
                    }
                } catch (Exception e) {
                    Log.err("Failed to update map stats", e);
                }
            }

            if (pluginState.restartOnGameOver) restart();
        });
    }

    private void restart() {
        AtomicInteger secondsLeft = new AtomicInteger(10);
        Timer.schedule(() -> {
            Groups.player.each((p) -> Call.announce(p.con, bundleService.format(Locale.of(p.locale),
                            "server-restart-countdown",
                            args("seconds", secondsLeft.get()))));
            if (secondsLeft.decrementAndGet() == 0) {
                netServer.kickAll(Packets.KickReason.serverRestarting);
                System.exit(0);
            }
        }, 0, 1);
    }
}