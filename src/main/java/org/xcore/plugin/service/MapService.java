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
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.EventDataRepository;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.EventData;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.model.enums.Feature;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.VoteNewWave;
import org.xcore.plugin.vote.VoteNewWaveFactory;
import org.xcore.plugin.vote.VoteRtv;
import org.xcore.plugin.vote.VoteRtvFactory;
import org.xcore.plugin.vote.VoteService;

import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.state;

import static mindustry.Vars.maps;

@Singleton
public class MapService {
    private static final int NEW_VOTE_REPUTATION_DELTA = 1;
    private static final int CHANGED_VOTE_REPUTATION_DELTA = 2;
    private static final double POPULARITY_PER_REPUTATION = 2.0;
    private static final double NEGATIVE_POPULARITY_FACTOR = -POPULARITY_PER_REPUTATION;

    private final EventDataRepository eventDataRepository;
    private final MapDataRepository mapDataRepository;
    private final SessionService sessionService;
    private final TomlXcoreConfig config;
    private final TomlSecretsConfig secretsConfig;
    private final VoteService voteService;
    private final VoteNewWaveFactory voteNewWaveFactory;
    private final VoteRtvFactory voteRtvFactory;
    private final GameStateService gameStateService;

    @Inject
    public MapService(EventDataRepository eventDataRepository,
                      MapDataRepository mapDataRepository,
                      SessionService sessionService,
                      TomlXcoreConfig config,
                      TomlSecretsConfig secretsConfig,
                      VoteService voteService,
                      VoteNewWaveFactory voteNewWaveFactory,
                      VoteRtvFactory voteRtvFactory,
                      GameStateService gameStateService) {
        this.eventDataRepository = eventDataRepository;
        this.mapDataRepository = mapDataRepository;
        this.sessionService = sessionService;
        this.config = config;
        this.secretsConfig = secretsConfig;
        this.voteService = voteService;
        this.voteNewWaveFactory = voteNewWaveFactory;
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

    public Map findPersistedMap(MapData mapData) {
        if (mapData == null) {
            return null;
        }

        Map byFileName = findMapByFileName(mapData.fileName);
        if (byFileName != null) {
            return byFileName;
        }

        return getAvailableMaps().find(map ->
                map.plainName().equals(mapData.name)
                        && map.author().equals(mapData.author)
        );
    }

    public Map findMapByFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        String normalized = fileName.trim();
        return getAvailableMaps().find(map ->
                map.file != null && map.file.name().equalsIgnoreCase(normalized)
        );
    }

    public Map resolveNextMap(Gamemode mode, Map previous) {
        Map eventMap = findActiveEventMap();
        if (eventMap != null) {
            return eventMap;
        }

        return maps.getNextMap(mode, previous);
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

        if (isFeatureDisabled(Feature.RTV)) {
            session.locale().send("error-feature-disabled");
            return;
        }

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

        if (!isAllowedEventMap(target)) {
            session.locale().send("error-map-not-event");
            return;
        }

        if (forced) {
            switchMapImmediately(target);
            broadcastForcedMapSwitch(player, target);
        } else {
            startMapVote(player, target, isManual);
        }
    }

    public void startNewWaveSession(Player player, boolean forced) {
        var session = sessionService.get(player.uuid());

        if (isFeatureDisabled(Feature.VNW)) {
            session.locale().send("error-feature-disabled");
            return;
        }

        if (!state.rules.waves) {
            session.locale().send("error-wave-vote-unavailable");
            return;
        }

        if (voteService.shouldBlockVoteStart(VoteNewWave.class, forced)) {
            session.locale().send("error-vote-in-progress");
            return;
        }

        if (forced && voteService.isVoting()) {
            voteService.endVote();
        }

        if (forced) {
            skipWaveImmediately(player);
            return;
        }

        startWaveVote(player);
    }

    public void handleReputation(Player player, boolean like) {
        if (Vars.state.map == null) return;
        MapData map = mapDataRepository.findOrCreate(Vars.state.map.plainName(), Vars.state.map.file.name(), Vars.state.map.author(), Vars.state.rules.mode().name());
        handleReputation(player, like, map);
    }

    public void handleReputation(Player player, boolean like, MapData map) {
        var session = sessionService.get(player.uuid());
        Boolean previousVote = session.data.mapVotes.get(map.id.toString());

        if (Boolean.valueOf(like).equals(previousVote)) {
            session.locale().send("error-already-voted");
            return;
        }

        VoteDelta delta = buildVoteDelta(previousVote, like);
        applyVoteDelta(map, delta);
        session.locale().send(delta.messageKey());

        sessionService.putMapVote(session, map.id.toString(), like);
        mapDataRepository.applyVote(map.id, delta.reputationDelta(), delta.popularityDelta(), delta.likeDelta(), delta.dislikeDelta());
    }

    private boolean isAllowedEventMap(Map target) {
        EventData event = getActiveEventOrNull();
        if (event == null) {
            return true;
        }

        MapData mapData = mapDataRepository.findOrCreate(
                target.name(),
                target.file.name(),
                target.author(),
                state.rules.mode().name()
        );
        return event.map.equals(mapData.id);
    }

    private Map findActiveEventMap() {
        EventData event = getActiveEventOrNull();
        if (event == null) {
            return null;
        }

        MapData mapData = mapDataRepository.findById(event.map);
        if (mapData == null) {
            return null;
        }

        return findPersistedMap(mapData);
    }

    private void switchMapImmediately(Map target) {
        Timer.schedule(
                () -> gameStateService.reloadWorld(() -> {
                    Gamemode mode = Gamemode.valueOf(arc.Core.settings.getString("lastServerMode", "survival"));
                    Vars.world.loadMap(target, target.applyRules(mode));
                }),
                secretsConfig.maps.voting.switchDelaySeconds
        );
    }

    private void broadcastForcedMapSwitch(Player player, Map target) {
        sessionService.broadcast(
                "commands-artv-map-skipped",
                args("name", target.name(), "nickname", player.coloredName())
        );
    }

    private void startMapVote(Player player, Map target, boolean isManual) {
        var vote = voteRtvFactory.create(target, isManual);
        voteService.startVote(vote);
        vote.vote(player, 1);
    }

    private void startWaveVote(Player player) {
        var vote = voteNewWaveFactory.create(state.wave);
        voteService.startVote(vote);
        vote.vote(player, 1);
    }

    private void skipWaveImmediately(Player player) {
        Vars.logic.skipWave();
        sessionService.broadcast("notification-admin-wave-skip", args(
                "admin", player.coloredName()
        ));
    }

    private EventData getActiveEventOrNull() {
        if (!isEvent()) {
            return null;
        }

        EventData event = eventDataRepository.findActive().orElse(null);
        return event != null && event.isActive ? event : null;
    }

    private boolean isFeatureDisabled(Feature feature) {
        return config.runtime.disabledFeatures.contains(feature.key());
    }

    private boolean isEvent() {
        return "event".equals(config.server.name);
    }

    private VoteDelta buildVoteDelta(Boolean previousVote, boolean like) {
        int reputationMagnitude = previousVote == null ? NEW_VOTE_REPUTATION_DELTA : CHANGED_VOTE_REPUTATION_DELTA;
        int reputationDelta = like ? reputationMagnitude : -reputationMagnitude;
        double popularityDelta = reputationMagnitude * (like ? POPULARITY_PER_REPUTATION : NEGATIVE_POPULARITY_FACTOR);
        int likeDelta = like ? 1 : previousVote != null ? -1 : 0;
        int dislikeDelta = like ? previousVote != null ? -1 : 0 : 1;
        String messageKey = like
                ? previousVote != null ? "like-map-changed" : "like-map-success"
                : previousVote != null ? "dislike-map-changed" : "dislike-map-success";
        return new VoteDelta(reputationDelta, popularityDelta, likeDelta, dislikeDelta, messageKey);
    }

    private void applyVoteDelta(MapData map, VoteDelta delta) {
        map.reputation += delta.reputationDelta();
        map.popularity += delta.popularityDelta();
        map.like += delta.likeDelta();
        map.dislike += delta.dislikeDelta();
    }

    private record VoteDelta(int reputationDelta, double popularityDelta, int likeDelta, int dislikeDelta, String messageKey) {
    }
}
