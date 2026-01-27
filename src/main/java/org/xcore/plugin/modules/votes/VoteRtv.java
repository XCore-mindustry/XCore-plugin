package org.xcore.plugin.modules.votes;

import arc.Core;
import arc.util.Timer;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.modules.bundles.BundleService;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.utils.models.MapData;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;
import static mindustry.Vars.world;
import static org.xcore.plugin.utils.Utils.reloadWorld;

public class VoteRtv extends VoteSession {

    public final Map target;
    public final boolean isManualSelection;

    private final DatabaseService database;
    private final GlobalConfig globalConfig;
    private final BundleService bundle;
    private final VoteService voteService;

    public VoteRtv(Map target, boolean isManualSelection, DatabaseService database,
                   GlobalConfig globalConfig, BundleService bundleService,
                   VoteService voteService) {
        super(globalConfig);

        this.target = target;
        this.isManualSelection = isManualSelection;
        this.database = database;
        this.globalConfig = globalConfig;
        this.bundle = bundleService;
        this.voteService = voteService;
    }

    @Override
    public void vote(Player player, int sign) {
        super.vote(player, sign);
        bundle.send("rtv-vote", args(
                "nickname", player.coloredName(),
                "mapName", target.name(),
                "votes", votes(),
                "votesRequired", votesRequired()));
    }

    @Override
    public void left(Player player) {
        if (voted.remove(player.id) != 0) {
            bundle.send("rtv-left", args(
                    "nickname", player.coloredName(),
                    "votes", votes(),
                    "votesRequired", votesRequired()));
        }
    }

    @Override
    public void success() {
        stop();
        bundle.send("rtv-success", args(
                "mapName", target.name(),
                "mapLoadDelay", globalConfig.mapSwitchDelaySeconds));

        if (state.map != null && !state.isMenu()) {
            String currentMapName = state.map.plainName();
            String currentAuthor = state.map.author();
            String currentMode = state.rules.mode().name();

            MapData currentMapStats = database.getMapDataRepository().find(currentMapName, currentAuthor, currentMode);
            currentMapStats.onSkip();
            database.getMapDataRepository().save(currentMapStats);
        }

        if (isManualSelection) {
            String targetMapName = target.plainName();
            String targetAuthor = target.author();
            String targetMode = state.rules.mode().name();

            MapData targetMapStats = database.getMapDataRepository().find(targetMapName, targetAuthor, targetMode);
            targetMapStats.popularity += 2.0;
            database.getMapDataRepository().save(targetMapStats);
        }

        Timer.schedule(() -> reloadWorld(() ->
                        world.loadMap(target, target.applyRules(Gamemode.valueOf(Core.settings.getString("lastServerMode"))))),
                globalConfig.mapSwitchDelaySeconds);
    }

    @Override
    public void fail() {
        stop();
        bundle.send("rtv-fail", args("mapName", target.name()));
    }

    @Override
    public void stop() {
        voteService.endVote();
        end.cancel();
    }
}
