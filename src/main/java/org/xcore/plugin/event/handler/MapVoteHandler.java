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
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.Packets;
import mindustry.server.ServerControl;
import mindustry.ui.Menus;
import org.xcore.plugin.command.controller.client.MapController;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.service.MenuService;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.ui.MenuSession;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;

@Singleton
public class MapVoteHandler {

    private final MapDataRepository mapDataRepository;
    private final PlayerSessionService playerSessionService;
    private final BundleService bundleService;
    private final MenuService menuService;
    private final Provider<MapController> mapController;
    private final GameDataService gameDataService;
    private final EventDataRepository eventDataRepository;
    private final Config config;


    @Inject
    public MapVoteHandler(MapDataRepository mapDataRepository,
                          PlayerSessionService playerSessionService,
                          BundleService bundleService,
                          MenuService menuService,
                          Provider<MapController> mapController,
                          GameDataService gameDataService,
                          EventDataRepository eventDataRepository,
                          Config config) {
        this.mapDataRepository = mapDataRepository;
        this.playerSessionService = playerSessionService;
        this.bundleService = bundleService;
        this.menuService = menuService;
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
                    String menuTitle = bundleService.format(bundleService.locale(p), "map-vote-title", args());

                    String menuContent = bundleService.format(bundleService.locale(p), "map-vote-content", args(
                            "mapName", nextName,
                            "author", nextAuthor,
                            "seconds", 10
                    ));

                    PlayerData pData = playerSessionService.get(p.uuid());
                    Boolean currentVote = pData.mapVotes.get(mapData.id.toString());

                    String likeButtonText = Boolean.TRUE.equals(currentVote)
                            ? bundleService.format(bundleService.locale(p), "map-vote-like-selected", args())
                            : bundleService.format(bundleService.locale(p), "map-vote-like", args());
                    String dislikeButtonText = Boolean.FALSE.equals(currentVote)
                            ? bundleService.format(bundleService.locale(p), "map-vote-dislike-selected", args())
                            : bundleService.format(bundleService.locale(p), "map-vote-dislike", args());

                    MenuSession session = menuService.get(p.uuid());
                    session.actions.clear();
                    List<List<String>> rows = new ArrayList<>();

                    List<String> row1 = new ArrayList<>();
                    row1.add(session.add(likeButtonText, () -> mapController.get().handleReputation(p, true, mapData)));
                    row1.add(session.add(dislikeButtonText, () -> mapController.get().handleReputation(p, false, mapData)));
                    rows.add(row1);

                    List<String> row2 = new ArrayList<>();
                    row2.add(session.add(bundleService.format(bundleService.locale(p), "current-map", args()), () -> {
                        session.clearHistory();
                        mapController.get().handleMap(p, mapData);
                    }));
                    row2.add(session.add(bundleService.format(bundleService.locale(p), "next-map", args()), () -> {
                        session.clearHistory();
                        mapController.get().handleMap(p, nextMapData);
                    }));
                    rows.add(row2);

                    menuService.addNavigationRow(p, session, rows);

                    Call.menu(p.con, menuService.getMenuId(), menuTitle, menuContent, menuService.convertListToArray(rows));
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
