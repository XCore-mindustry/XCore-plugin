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
import org.xcore.plugin.command.controller.client.MapController;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.menu.MapMenu;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;

@Singleton
public class MapVoteHandler {

    private final MapDataRepository mapDataRepository;
    private final SessionService sessionService;
    private final Provider<MapController> mapController;
    private final Provider<MapMenu> mapMenu;
    private final GameDataService gameDataService;
    private final EventDataRepository eventDataRepository;
    private final Config config;


    @Inject
    public MapVoteHandler(MapDataRepository mapDataRepository,
                          SessionService sessionService,
                          Provider<MapController> mapController, Provider<MapMenu> mapMenu,
                          GameDataService gameDataService,
                          EventDataRepository eventDataRepository,
                          Config config) {
        this.mapDataRepository = mapDataRepository;
        this.sessionService = sessionService;
        this.mapMenu = mapMenu;
        this.mapController = mapController;
        this.gameDataService = gameDataService;
        this.eventDataRepository = eventDataRepository;
        this.config = config;
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

            gameDataService.finishGame();

            Map nextMap = maps.getNextMap(ServerControl.instance.lastMode, state.map);
            String nextName = (nextMap != null) ? nextMap.plainName() : "Unknown";
            String nextAuthor = (nextMap != null) ? nextMap.author() : "Unknown";

            if (nextMap != null) {
                state.gameOver = true;
                Call.updateGameOver(event.winner);

                MapData mapData = mapDataRepository.findOrCreate(state.map.plainName(), state.map.file.name(), state.map.author(), state.rules.mode().name());
                MapData nextMapData = mapDataRepository.findOrCreate(nextMap.plainName(), nextMap.file.name(), nextMap.author(), state.rules.mode().name());

                Groups.player.each(p -> {
                    Session session = sessionService.get(p.uuid()).clear();
                    PlayerData pData = session.data;
                    Localization local = session.locale();

                    Boolean currentVote = pData.mapVotes.get(mapData.id.toString());

                    String likeButtonText = Boolean.TRUE.equals(currentVote)
                            ? local.format("map-vote-like-selected", args())
                            : local.format("map-vote-like", args());
                    String dislikeButtonText = Boolean.FALSE.equals(currentVote)
                            ? local.format("map-vote-dislike-selected", args())
                            : local.format("map-vote-dislike", args());

                    var menu = session.builder().title("map-vote-title")
                            .content("map-vote-content", args(
                                "mapName", nextName,
                                "author", nextAuthor,
                                "seconds", 10
                            ))
                            .add(likeButtonText, () -> mapController.get().handleReputation(p, true, mapData))
                            .add(dislikeButtonText, () -> mapController.get().handleReputation(p, false, mapData))
                            .end()
                            .add("current-map", () -> {
                                session.clearHistory();
                                mapMenu.get().map(p.uuid(), mapData);
                            })
                            .add("next-map", () -> {
                                session.clearHistory();
                                mapMenu.get().map(p.uuid(), nextMapData);
                            })
                            .addNavigationRow().show();
                });


                gameDataService.startNewGame(nextMapData, state.rules.modeName, config.isEvent() ? eventDataRepository.findActive().orElse(null) : null);
                Groups.player.each(gameDataService::addPlayer);

                ServerControl.instance.play(() -> world.loadMap(nextMap,
                        nextMap.applyRules(ServerControl.instance.lastMode)));
            } else {
                netServer.kickAll(Packets.KickReason.gameover);
                state.set(GameState.State.menu);
                net.closeServer();
            }
        };
    }
}
