package org.xcore.plugin.event.handler;

import arc.func.Cons;
import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
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
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.PlayerSessionService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;

@Singleton
public class MapVoteHandler {

    private int mapVoteMenuId;

    private final MapDataRepository mapDataRepository;
    private final PlayerDataRepository playerDataRepository;
    private final PlayerSessionService playerSessionService;
    private final BundleService bundleService;

    @Inject
    public MapVoteHandler(MapDataRepository mapDataRepository,
                          PlayerDataRepository playerDataRepository,
                          PlayerSessionService playerSessionService,
                          BundleService bundleService) {
        this.mapDataRepository = mapDataRepository;
        this.playerDataRepository = playerDataRepository;
        this.playerSessionService = playerSessionService;
        this.bundleService = bundleService;
    }

    public int initMenu() {
        mapVoteMenuId = Menus.registerMenu(this::onMapVote);
        return mapVoteMenuId;
    }

    public void onMapVote(Player player, int selection) {
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
            pData.mapVotes.put(mData.id.toString(), true);
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
            pData.mapVotes.put(mData.id.toString(), false);
        }

        playerDataRepository.save(pData);
        mapDataRepository.save(mData);
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
                    MapData map = mapDataRepository.findOrCreate(state.map.plainName(), state.map.file.name(), state.map.author(), state.rules.mode().name());
                    Boolean currentVote = pData.mapVotes.get(map.id);

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
