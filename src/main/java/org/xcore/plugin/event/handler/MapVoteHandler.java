package org.xcore.plugin.event.handler;

import arc.func.Cons;
import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.core.GameState;
import mindustry.game.EventType.GameOverEvent;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.maps.Map;
import mindustry.net.Packets;
import mindustry.server.ServerControl;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.ui.menu.MapMenu;
import static mindustry.Vars.*;

@Singleton
public class MapVoteHandler {

    private final MapDataRepository mapDataRepository;
    private final Provider<MapMenu> mapMenu;
    private final GameDataService gameDataService;
    private final EventDataRepository eventDataRepository;
    private final Config config;
    private final MapService mapService;


    @Inject
    public MapVoteHandler(MapDataRepository mapDataRepository,
                          Provider<MapMenu> mapMenu,
                          GameDataService gameDataService,
                          EventDataRepository eventDataRepository,
                          Config config,
                          MapService mapService) {
        this.mapDataRepository = mapDataRepository;
        this.mapMenu = mapMenu;
        this.gameDataService = gameDataService;
        this.eventDataRepository = eventDataRepository;
        this.config = config;
        this.mapService = mapService;
    }

    public Cons<GameOverEvent> getGameOverListener() {
        return event -> {
            if (state.rules.waves) {
                Log.info("Game over! Reached wave @ with @ players online on map @.",
                        state.wave, Groups.player.size(), Strings.capitalize(state.map.plainName()));
            } else {
                Log.info("Game over! Team @ is victorious with @ players online on map @.",
                        event.winner.name, Groups.player.size(), Strings.capitalize(state.map.plainName()));
            }

            gameDataService.finishGame(event.winner);

            Map nextMap = mapService.resolveNextMap(ServerControl.instance.lastMode, state.map);

            if (nextMap != null) {
                state.gameOver = true;
                Call.updateGameOver(event.winner);

                MapData mapData = mapDataRepository.findOrCreate(state.map.plainName(), state.map.file.name(), state.map.author(), state.rules.mode().name());
                MapData nextMapData = mapDataRepository.findOrCreate(nextMap.plainName(), nextMap.file.name(), nextMap.author(), state.rules.mode().name());

                mapMenu.get().showGameOverMenu(mapData, nextMapData, event.winner);


                gameDataService.startNewGame(nextMapData, state.rules.modeName, config.isEvent() ? eventDataRepository.findActive().orElse(null) : null);
                Groups.player.each(gameDataService::addPlayer);

                ServerControl.instance.play(() -> {
                    String nextMapName = nextMap.plainName();
                    String nextMapFile = nextMap.file == null ? "<null>" : nextMap.file.name();
                    String nextMapAuthor = nextMap.author();
                    String nextMapMode = ServerControl.instance.lastMode == null
                            ? "<null>"
                            : ServerControl.instance.lastMode.name();

                    Log.err("About to load next map '@' (file='@', author='@', mode='@')",
                            nextMapName, nextMapFile, nextMapAuthor, nextMapMode);

                    try {
                        world.loadMap(nextMap, nextMap.applyRules(ServerControl.instance.lastMode));
                    } catch (Throwable t) {
                        Log.err("Failed to load next map '@' (file='@', author='@', mode='@')", t,
                                nextMapName, nextMapFile, nextMapAuthor, nextMapMode);
                        throw t;
                    }
                });
            } else {
                netServer.kickAll(Packets.KickReason.gameover);
                state.set(GameState.State.menu);
                net.closeServer();
            }
        };
    }
}
