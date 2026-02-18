package org.xcore.plugin.service;

import arc.func.Boolf;
import arc.struct.Seq;
import arc.util.Timer;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Gamemode;
import mindustry.gen.Player;
import mindustry.maps.Map;
import org.xcore.plugin.common.TextUtils;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteRtv;
import org.xcore.plugin.vote.VoteRtvFactory;
import org.xcore.plugin.vote.VoteService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

import static mindustry.Vars.maps;

@Singleton
public class MapService {
    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;
    private final SessionService sessionService;
    private final Config config;
    private final GlobalConfig globalConfig;
    private final VoteService voteService;
    private final VoteRtvFactory voteRtvFactory;
    private final GameStateService gameStateService;

    @Inject
    public MapService(EventDataRepository eventDataRepository,
                      MapDataRepository mapDataRepository,
                      SessionService sessionService,
                      Config config,
                      GlobalConfig globalConfig,
                      VoteService voteService,
                      VoteRtvFactory voteRtvFactory,
                      GameStateService gameStateService) {
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.sessionService = sessionService;
        this.config = config;
        this.globalConfig = globalConfig;
        this.voteService = voteService;
        this.voteRtvFactory = voteRtvFactory;
        this.gameStateService = gameStateService;
    }

    public Seq<Map> getAvailableMaps() {
        return maps.customMaps().isEmpty() ? maps.defaultMaps() : maps.customMaps();
    }

    public Map findMap(String nameOrIndex) {
        Seq<Map> available = getAvailableMaps();

        int index = Strings.parseInt(nameOrIndex, -1) - 1;
        if (index >= 0 && index < available.size) {
            return available.get(index);
        }

        return available.find(map -> TextUtils.deepEquals(map.name(), nameOrIndex));
    }

    public <T> T findInSeq(String nameOrIndex, Seq<T> values, Boolf<T> filter) {
        int index = Strings.parseInt(nameOrIndex, -1) - 1;
        if (index >= 0 && index < values.size) {
            return values.get(index);
        }
        return values.find(filter);
    }

    public void startRtvSession(Player player, Map target, boolean isManual, boolean forced) {
        var session = sessionService.get(player.uuid());

        if (voteService.shouldBlockVoteStart(VoteRtv.class, forced)) {
            session.locale().send("error-vote-in-progress");
            return;
        }

        if (forced && voteService.isVoting()) {
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
                Gamemode mode = Gamemode.valueOf(arc.Core.settings.getString("lastServerMode", "survival"));
                Vars.world.loadMap(target, target.applyRules(mode));
            }), globalConfig.mapSwitchDelaySeconds);

            sessionService.broadcast("commands-artv-map-skipped", args("name", target.name(), "nickname", player.coloredName()));
        } else {
            var vote = voteRtvFactory.create(target, isManual);
            voteService.startVote(vote);
            vote.vote(player, 1);
        }
    }

    public void handleReputation(Player player, boolean like) {
        if (Vars.state.map == null) return;
        MapData map = mapDataRepository.findOrCreate(Vars.state.map.plainName(), Vars.state.map.file.name(), Vars.state.map.author(), Vars.state.rules.mode().name());
        handleReputation(player, like, map);
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
