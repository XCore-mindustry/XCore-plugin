package org.xcore.plugin.vote;

import arc.Core;
import arc.util.Timer;
import io.avaje.inject.AssistFactory;
import io.avaje.inject.Assisted;
import jakarta.inject.Inject;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.service.GameStateService;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.enums.FinishReason;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;
import mindustry.gen.Groups;

import static mindustry.Vars.state;
import static mindustry.Vars.world;

@AssistFactory(VoteRtvFactory.class)
public class VoteRtv extends VoteSession {
    public final Map target;
    public final boolean isManualSelection;

    private final MapDataRepository mapDataRepository;
    private final TomlSecretsConfig secretsConfig;
    private final SessionService sessionService;
    private final GameStateService gameStateService;
    private final GameDataService gameDataService;
    private final org.xcore.plugin.service.map.MapVoteObserverService mapVoteObserverService;

    @Inject
    public VoteRtv(
            @Assisted Map target,
            @Assisted boolean isManualSelection,

            MapDataRepository mapDataRepository,
            TomlSecretsConfig secretsConfig,
            SessionService sessionService,
            VoteService voteService,
            GameStateService gameStateService,
            GameDataService gameDataService,
            org.xcore.plugin.service.map.MapVoteObserverService mapVoteObserverService) {
        super(secretsConfig, voteService);
        this.target = target;
        this.isManualSelection = isManualSelection;
        this.mapDataRepository = mapDataRepository;
        this.secretsConfig = secretsConfig;
        this.sessionService = sessionService;
        this.gameStateService = gameStateService;
        this.gameDataService = gameDataService;
        this.mapVoteObserverService = mapVoteObserverService;
    }

    public VoteRtv(
            Map target,
            boolean isManualSelection,
            MapDataRepository mapDataRepository,
            TomlSecretsConfig secretsConfig,
            SessionService sessionService,
            VoteService voteService,
            GameStateService gameStateService,
            GameDataService gameDataService) {
        this(target, isManualSelection, mapDataRepository, secretsConfig, sessionService, voteService, gameStateService, gameDataService, null);
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        sessionService.broadcast("rtv-vote", args(
                "nickname", player.coloredName(),
                "mapName", target.name(),
                "votes", votes(),
                "votesRequired", votesRequired()));

        if (mapVoteObserverService != null) {
            String mapId = target.file != null ? target.file.name() : target.plainName();
            int remaining = end != null ? (int) Math.max(0, (end.getExecuteTimeMillis() - System.currentTimeMillis()) / 1000L) : 0;
            mapVoteObserverService.notifyVoteProgress(mapId, votes(), votesRequired(), remaining);
        }
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0) {
            sessionService.broadcast("rtv-left", args(
                    "nickname", player.coloredName(),
                    "votes", votes(),
                    "votesRequired", votesRequired()));

            if (mapVoteObserverService != null) {
                String mapId = target.file != null ? target.file.name() : target.plainName();
                int remaining = end != null ? (int) Math.max(0, (end.getExecuteTimeMillis() - System.currentTimeMillis()) / 1000L) : 0;
                mapVoteObserverService.notifyVoteProgress(mapId, votes(), votesRequired(), remaining);
            }
        }
    }

    @Override
    public void success() {
        stop();
        if (mapVoteObserverService != null) {
            String mapId = target.file != null ? target.file.name() : target.plainName();
            mapVoteObserverService.notifyVoteEnded(mapId);
        }
        sessionService.broadcast("rtv-success", args(
                "mapName", target.name(),
                "mapLoadDelay", secretsConfig.maps.voting.switchDelaySeconds));

        if (state.map != null && !state.isMenu()) {
            String currentMapName = state.map.plainName();
            String currentMapFileName = state.map.file.name();
            String currentAuthor = state.map.author();
            String currentMode = state.rules.mode().name();

            MapData currentMapStats = mapDataRepository.findOrCreate(currentMapName, currentMapFileName, currentAuthor, currentMode);
            currentMapStats.onSkip();
            mapDataRepository.markSkip(currentMapStats.id);
        }

        if (isManualSelection) {
            String targetMapName = target.plainName();
            String targetMapFileName = state.map.file.name();
            String targetAuthor = target.author();
            String targetMode = state.rules.mode().name();

            MapData targetMapStats = mapDataRepository.findOrCreate(targetMapName, targetMapFileName, targetAuthor, targetMode);
            targetMapStats.popularity += 2.0;
            mapDataRepository.bumpPopularity(targetMapStats.id, 2.0);
        }

        Timer.schedule(() -> {
                    gameDataService.finishGame(null, FinishReason.RTV);
                    gameStateService.reloadWorld(() ->
                                    world.loadMap(target, target.applyRules(Gamemode.valueOf(Core.settings.getString("lastServerMode")))),
                            () -> {
                                gameDataService.startNewGame(
                                        mapDataRepository.findOrCreate(target.plainName(), target.file.name(), target.author(), state.rules.mode().name()),
                                        state.rules.modeName,
                                        null
                                );
                                Groups.player.each(gameDataService::addPlayer);
                            });
                },
                secretsConfig.maps.voting.switchDelaySeconds);
    }

    @Override
    public void fail() {
        stop();
        if (mapVoteObserverService != null) {
            String mapId = target.file != null ? target.file.name() : target.plainName();
            mapVoteObserverService.notifyVoteEnded(mapId);
        }
        sessionService.broadcast("rtv-fail", args("mapName", target.name()));
    }

    @Override
    public void cancelByAdmin(Player admin) {
        stop();
        if (mapVoteObserverService != null) {
            String mapId = target.file != null ? target.file.name() : target.plainName();
            mapVoteObserverService.notifyVoteEnded(mapId);
        }
        sessionService.broadcast("rtv-cancelled", args(
                "mapName", target.name(),
                "admin", admin.coloredName()));
    }
}
