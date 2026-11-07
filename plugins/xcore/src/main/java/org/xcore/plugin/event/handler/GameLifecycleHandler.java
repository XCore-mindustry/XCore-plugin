package org.xcore.plugin.event.handler;

import arc.util.Log;
import arc.util.Strings;
import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.mindustry.Messenger;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.Rules;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.io.JsonIO;
import mindustry.net.Packets;
import org.xcore.protocol.generated.messages.server.ServerMessages.ServerActionV1;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.GameData;
import org.xcore.plugin.model.enums.FinishReason;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.service.MapStatsService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.startup.ShutdownOrder;

import java.util.concurrent.atomic.AtomicInteger;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;

@Singleton
public class GameLifecycleHandler {

    private final Async async;
    private final MapDataRepository maps;
    private final NetworkService network;
    private final TomlXcoreConfig config;
    private final Bundle bundle;
    private final PluginState pluginState;
    private final GameDataService gameDataService;
    private final MapStatsService mapStatsService;
    private final ScheduledTasks tasks = new ScheduledTasks(GameLifecycleHandler.class);
    /** Waits between map lookups for the running match; the last one repeats until the match ends. */
    private static final float[] MAP_RETRY_SECONDS = {5, 30, 60};

    @Inject
    public GameLifecycleHandler(Async async,
                                MapDataRepository maps,
                                NetworkService network,
                                TomlXcoreConfig config,
                                Bundle bundle,
                                PluginState pluginState,
                                GameDataService gameDataService,
                                MapStatsService mapStatsService) {
        this.async = async;
        this.maps = maps;
        this.network = network;
        this.config = config;
        this.bundle = bundle;
        this.pluginState = pluginState;
        this.gameDataService = gameDataService;
        this.mapStatsService = mapStatsService;
    }

    public void onPlayEvent(PlayEvent event) {
        // Opt-in: with statistics off, HexedCore still relies on there being no lifecycle game.
        if (async != null && config.statistics.enabled && gameDataService.getCurrent() == null && state.map != null
                && state.map.file != null && !"external".equals(config.statistics.historyOwner)) {
            var loadedMap = state.map;
            String file = loadedMap.file.name(), name = loadedMap.plainName(), author = loadedMap.author();
            // modeName classifies the game; the map record is keyed by the canonical mode, as everywhere else.
            var game = gameDataService.startGameWithoutMap(state.rules.modeName);
            if (game != null) {
                Groups.player.each(gameDataService::addPlayer);
                resolveMap(game, name, file, author, state.rules.mode().name(), 0);
            }
        }
        String mapName = state.map == null ? "<null>" : state.map.plainName();
        String mapFile = state.map == null || state.map.file == null ? "<null>" : state.map.file.name();
        String rawRules = state.map == null ? null : state.map.tags.get("rules");

        if (rawRules == null || rawRules.isBlank()) {
            Log.err("Loaded map '@' (file='@') without rules tag; skipping JsonIO rules merge",
                    mapName, mapFile);
            return;
        }

        var mapRules = JsonIO.read(Rules.class, rawRules);
        if (mapRules != null) {
            state.rules.bannedBlocks.addAll(mapRules.bannedBlocks);
            state.rules.bannedUnits.addAll(mapRules.bannedUnits);
            state.rules.revealedBlocks.addAll(mapRules.revealedBlocks);
            Log.info("@ banned blocks, @ banned units, @ revealed blocks",
                    mapRules.bannedBlocks.size, mapRules.bannedUnits.size, mapRules.revealedBlocks.size);
        }
    }

    /** Looks the map up off the game thread, retrying while the match runs; capture goes on meanwhile. */
    private void resolveMap(GameData game, String name, String file, String author, String mode, int attempt) {
        async.supply(() -> maps.findOrCreate(name, file, author, mode)).thenMain((map, error) -> {
            if (gameDataService.getCurrent() != game) return;
            if (error == null) {
                gameDataService.attachMap(game, map);
                return;
            }
            float delay = MAP_RETRY_SECONDS[Math.min(attempt, MAP_RETRY_SECONDS.length - 1)];
            Log.err("[Stats] Cannot look up the map of the running match; retrying in @ s", delay, error);
            tasks.after(delay, () -> {
                if (gameDataService.getCurrent() == game) resolveMap(game, name, file, author, mode, attempt + 1);
            });
        });
    }

    public void onGameOver(GameOverEvent event) {
        String message = "Game over!";

        if (state.rules.waves) {
            message = Strings.format(
                    "Game over! Reached wave @ with @ players online on map @.",
                    state.wave, Groups.player.size(),
                    Strings.capitalize(Strings.stripColors(state.map.name())));
        } else if (state.rules.pvp) {
            message = Strings.format(
                    "Game over! Team @ is victorious with @ players online on map @.",
                    event.winner.name, Groups.player.size(),
                    Strings.capitalize(Strings.stripColors(state.map.name())));
        }

        network.post(new ServerActionV1(message, config.server.name));

        if (state.map != null && !state.isMenu()) {
            String fileName = state.map.file == null ? null : state.map.file.name();
            boolean isWin = event.winner != null && event.winner != state.rules.waveTeam;

            mapStatsService.registerCompletedGame(
                    state.map.plainName(),
                    fileName,
                    state.map.author(),
                    state.rules.mode().name(),
                    (long) ((state.tick / 60f) * 1000f),
                    isWin
            );
        }

        if (pluginState.restartOnGameOver) restart();
    }

    public void onWorldReload(String event) {
        if (event == null || event.isBlank()) {
            return;
        }

        if (event.startsWith("rvsb_game_over:")) {
            handleRvsbGameOver(event.substring("rvsb_game_over:".length()));
            return;
        }

        if ((event.equals("rvsb_world-reload") || event.equals("rvsb-world-reload")) && pluginState.restartOnGameOver) {
            restart();
        }
    }

    private void handleRvsbGameOver(String winnerName) {
        if (winnerName == null || winnerName.isBlank()) {
            gameDataService.finishGame(null, FinishReason.NATURAL);
            return;
        }

        Team winner = null;
        for (var team : Team.all) {
            if (team != null && winnerName.equalsIgnoreCase(team.name)) {
                winner = team;
                break;
            }
        }
        gameDataService.finishGame(winner, FinishReason.NATURAL);
    }

    @PostConstruct
    public void start() {
        if (config.statistics.enabled) tasks.every(5, 5, gameDataService::syncTeams);
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        tasks.close();
    }

    private void restart() {
        AtomicInteger secondsLeft = new AtomicInteger(10);
        Messenger messenger = Messenger.of(bundle);
        tasks.every(0, 1, () -> {
            // Each player in their selected language, also those whose session is not loaded yet.
            messenger.all().announce("server-restart-countdown", args("seconds", secondsLeft.get()));
            if (secondsLeft.decrementAndGet() == 0) {
                netServer.kickAll(Packets.KickReason.serverRestarting);
                System.exit(0);
            }
        });
    }
}
