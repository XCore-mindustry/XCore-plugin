package org.xcore.plugin.event.handler;

import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.GameOverEvent;
import mindustry.game.EventType.PlayEvent;
import mindustry.game.Rules;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.io.JsonIO;
import mindustry.net.Packets;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.event.SocketEvents;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;

@Singleton
public class GameLifecycleHandler {

    private final MapDataRepository mapDataRepository;
    private final NetworkService network;
    private final Config config;
    private final BundleService bundleService;
    private final PluginState pluginState;

    @Inject
    public GameLifecycleHandler(MapDataRepository mapDataRepository,
                                NetworkService network,
                                Config config,
                                BundleService bundleService,
                                PluginState pluginState) {
        this.mapDataRepository = mapDataRepository;
        this.network = network;
        this.config = config;
        this.bundleService = bundleService;
        this.pluginState = pluginState;
    }

    public void onPlayEvent(PlayEvent event) {
        pluginState.gameStartTime = Time.millis();

        var mapRules = JsonIO.read(Rules.class, state.map.tags.get("rules"));
        if (mapRules != null) {
            state.rules.bannedBlocks.addAll(mapRules.bannedBlocks);
            state.rules.bannedUnits.addAll(mapRules.bannedUnits);
            state.rules.revealedBlocks.addAll(mapRules.revealedBlocks);
            Log.info("@ banned blocks, @ banned units, @ revealed blocks",
                    mapRules.bannedBlocks.size, mapRules.bannedUnits.size, mapRules.revealedBlocks.size);
        }
    }

    public void onGameOver(GameOverEvent event) {
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
                String fileName = state.map.file.name();
                String author = state.map.author();
                String modeName = state.rules.mode().name();

                long durationMillis = (long) ((state.tick / 60f) * 1000f);

                if (durationMillis > 120 * 1000) {
                    MapData stats = mapDataRepository.findOrCreate(mapName, fileName, author, modeName);
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
    }

    public void onWorldReload(String event) {
        if ((event.equals("rvsb_world-reload") || event.equals("hexed_world-reload")) && pluginState.restartOnGameOver) {
            restart();
        }
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
