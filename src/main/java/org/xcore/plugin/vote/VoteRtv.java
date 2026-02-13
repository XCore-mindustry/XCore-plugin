package org.xcore.plugin.vote;

import arc.Core;
import arc.util.Timer;
import io.avaje.inject.AssistFactory;
import io.avaje.inject.Assisted;
import jakarta.inject.Inject;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.GameStateService;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;
import static mindustry.Vars.world;

@AssistFactory(VoteRtvFactory.class)
public class VoteRtv extends VoteSession {
    public final Map target;
    public final boolean isManualSelection;

    private final MapDataRepository mapDataRepository;
    private final GlobalConfig globalConfig;
    private final SessionService sessionService;
    private final VoteService voteService;
    private final GameStateService gameStateService;

    @Inject
    public VoteRtv(
            @Assisted Map target,
            @Assisted boolean isManualSelection,

            MapDataRepository mapDataRepository,
            GlobalConfig globalConfig,
            SessionService sessionService,
            VoteService voteService,
            GameStateService gameStateService) {
        super(globalConfig);
        this.target = target;
        this.isManualSelection = isManualSelection;
        this.mapDataRepository = mapDataRepository;
        this.globalConfig = globalConfig;
        this.sessionService = sessionService;
        this.voteService = voteService;
        this.gameStateService = gameStateService;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        sessionService.broadcast("rtv-vote", args(
                "nickname", player.coloredName(),
                "mapName", target.name(),
                "votes", votes(),
                "votesRequired", votesRequired()));
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0) {
            sessionService.broadcast("rtv-left", args(
                    "nickname", player.coloredName(),
                    "votes", votes(),
                    "votesRequired", votesRequired()));
        }
    }

    @Override
    public void success() {
        stop();
        sessionService.broadcast("rtv-success", args(
                "mapName", target.name(),
                "mapLoadDelay", globalConfig.mapSwitchDelaySeconds));

        if (state.map != null && !state.isMenu()) {
            String currentMapName = state.map.plainName();
            String currentMapFileName = state.map.file.name();
            String currentAuthor = state.map.author();
            String currentMode = state.rules.mode().name();

            MapData currentMapStats = mapDataRepository.findOrCreate(currentMapName, currentMapFileName, currentAuthor, currentMode);
            currentMapStats.onSkip();
            mapDataRepository.save(currentMapStats);
        }

        if (isManualSelection) {
            String targetMapName = target.plainName();
            String targetMapFileName = state.map.file.name();
            String targetAuthor = target.author();
            String targetMode = state.rules.mode().name();

            MapData targetMapStats = mapDataRepository.findOrCreate(targetMapName, targetMapFileName, targetAuthor, targetMode);
            targetMapStats.popularity += 2.0;
            mapDataRepository.save(targetMapStats);
        }

        Timer.schedule(() -> gameStateService.reloadWorld(() ->
                        world.loadMap(target, target.applyRules(Gamemode.valueOf(Core.settings.getString("lastServerMode"))))),
                globalConfig.mapSwitchDelaySeconds);
    }

    @Override
    public void fail() {
        stop();
        sessionService.broadcast("rtv-fail", args("mapName", target.name()));
    }

    @Override
    public void cancelByAdmin(Player admin) {
        stop();
        sessionService.broadcast("rtv-cancelled", args(
                "mapName", target.name(),
                "admin", admin.coloredName()));
    }

    @Override
    public void stop() {
        voteService.endVote();
        end.cancel();
    }
}
