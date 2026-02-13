package org.xcore.plugin.command.controller.client;

import arc.Core;
import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.GameStateService;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.MapMenu;
import org.xcore.plugin.vote.VoteRtv;
import org.xcore.plugin.vote.VoteRtvFactory;
import org.xcore.plugin.vote.VoteService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

@Singleton
public class MapController implements CloudClientController {

    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;
    private final SessionService sessionService;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final VoteService voteService;
    private final VoteRtvFactory voteRtvFactory;
    private final GameStateService gameStateService;
    private final Provider<MapMenu> menu;

    @Inject
    public MapController(
            EventDataRepository eventDataRepository,
            MapDataRepository mapDataRepository,
            PlayerDataRepository playerDataRepository,
            SessionService sessionService,
            Config config,
            GlobalConfig globalConfig,
            VoteService voteService,
            VoteRtvFactory voteRtvFactory,
            MapService mapService,
            GameStateService gameStateService,
            Provider<MapMenu> menu
    ) {
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.sessionService = sessionService;
        this.config = config;
        this.globalConfig = globalConfig;
        this.voteService = voteService;
        this.voteRtvFactory = voteRtvFactory;
        this.gameStateService = gameStateService;
        this.menu = menu;
    }

    @Command("map|map-stats|map-statistics")
    public void map(XCoreSender sender) {
        map(sender, Vars.state.map);
    }

    @Command("map|map-stats|map-statistics <map>")
    public void map(XCoreSender sender, @Argument("map") Map map) {
        if (map == null) {
            sender.send("error-map-not-found", args());
            return;
        }

        MapData data = mapDataRepository.findOrCreate(
                map.plainName(), map.file.name(), map.author(), Vars.state.rules.mode().name()
        );

        menu.get().map(menu.get().getUuid(sender), data);
    }

    @Command("maps|map-ui [page]")
    public void maps(XCoreSender sender, @Argument("page") @Default("1") int page) {
        menu.get().maps(menu.get().getUuid(sender), page);
    }

    @Command("rtv [map]")
    public void rtv(XCoreSender sender, @Argument("map") Map map) {
        Map target = map != null ? map : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);
        startRtvSession(sender.player(), target, map != null, false);
    }

    @Permission("admin")
    @Command("artv [map]")
    public void artv(XCoreSender sender, @Argument("map") Map map) {
        Map target = map != null ? map : Vars.maps.getNextMap(Vars.state.rules.mode(), Vars.state.map);
        startRtvSession(sender.player(), target, map != null, true);
    }

    @Command("like|+")
    public void like(XCoreSender sender) {
        handleReputation(sender.player(), true);
    }

    @Command("dislike|-")
    public void dislike(XCoreSender sender) {
        handleReputation(sender.player(), false);
    }

    public void startRtvSession(Player player, Map target, boolean isManual, boolean forced) {
        var session = sessionService.get(player.uuid());

        if (voteService.isVoting() && !(voteService.getCurrentSession() instanceof VoteRtv)) {
            session.locale().send("error-vote-in-progress");
            return;
        } else if (voteService.isVoting() && !forced) {
            session.locale().send("error-vote-in-progress");
            return;
        } else if (voteService.isVoting() && forced) {
            voteService.endVote();
        }

        if (target == null) {
            session.locale().send("error-map-not-found");
            return;
        }

        if (config.isEvent()) {
            EventData event = eventDataRepository.findActive().orElse(null);
            if (event != null && event.isActive) {
                MapData mapData = mapDataRepository.findOrCreate(target.name(), target.file.name(), target.author(), state.rules.mode().name());
                if (!event.map.equals(mapData.id)) {
                    session.locale().send("error-map-not-event");
                    return;
                }
            }
        }

        if (forced) {
            Timer.schedule(() -> gameStateService.reloadWorld(() -> {
                Gamemode mode = Gamemode.valueOf(Core.settings.getString("lastServerMode", "survival"));
                Vars.world.loadMap(target, target.applyRules(mode));
            }), globalConfig.mapSwitchDelaySeconds);

            sessionService.broadcast("commands-artv-map-skipped", args("name", target.name(), "nickname", player.coloredName()));
        } else {
            var vote = voteRtvFactory.create(target, isManual);
            voteService.startVote(vote);
            vote.vote(player, 1);
        }
    }

    private void handleReputation(Player player, boolean like) {
        if (Vars.state.map == null) return;
        MapData m = mapDataRepository.findOrCreate(Vars.state.map.plainName(), Vars.state.map.file.name(), Vars.state.map.author(), Vars.state.rules.mode().name());
        handleReputation(player, like, m);
    }

    public void handleReputation(Player player, boolean like, MapData map) {
        var session = sessionService.get(player.uuid());
        Boolean prev = session.data.mapVotes.get(map.id.toString());

        if (Boolean.valueOf(like).equals(prev)) {
            session.locale().send("error-already-voted");
            return;
        }

        int mod = (prev == null) ? 1 : 2;
        if (like) {
            map.reputation += mod;
            map.popularity += (mod * 2.0);
            map.like += 1;
            if (prev != null) map.dislike -= 1;
            session.locale().send(prev != null ? "like-map-changed" : "like-map-success");
        } else {
            map.reputation -= mod;
            map.popularity -= (mod * 2.0);
            map.dislike += 1;
            if (prev != null) map.like -= 1;
            session.locale().send(prev != null ? "dislike-map-changed" : "dislike-map-success");
        }

        session.data.mapVotes.put(map.id.toString(), like);
        session.save();
        mapDataRepository.save(map);
    }
}