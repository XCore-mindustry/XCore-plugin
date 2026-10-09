package org.xcore.plugin.gamemode.pvp.rating;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.game.Team;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.integration.gamehistory.MatchHistoryRecord;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.ladder.MatchSettlement;
import org.xcore.plugin.rating.ladder.SettlementResult;
import org.xcore.plugin.rating.ladder.StandingMutation;
import org.xcore.plugin.rating.match.MatchParticipant;
import org.xcore.plugin.rating.match.MatchReport;
import org.xcore.plugin.rating.math.TeamEloCalculator;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class MiniPvPRatingSettler {
    /** Why an unrated match changed nobody's rating. */
    public static final String SKIP_NOT_ENOUGH_PLAYERS = "not_enough_players";
    /** Why a match did or did not change one participant's rating; the match history words them. */
    public static final String REASON_WINNER = "winner";
    public static final String REASON_DEFEATED = "defeated";
    public static final String REASON_SHORT_PLAY = "short_play";
    public static final String REASON_LATE_JOIN = "late_join";
    public static final String REASON_MATCH_UNRATED = "match_unrated";

    private final MiniPvPMatchTracker matchTracker;
    private final TeamEloCalculator calculator = new TeamEloCalculator();
    private final Ladder ladder;
    private final RatingPolicy policy;

    private final SessionService sessionService;
    private final PlayerDisplayRefreshService playerDisplayRefreshService;
    private final GameDataService gameDataService;
    private final Async async;

    private final AtomicBoolean settled = new AtomicBoolean(false);

    @Inject
    public MiniPvPRatingSettler(
            MiniPvPMatchTracker matchTracker,
            MiniPvPLadder miniPvPLadder,
            SessionService sessionService,
            PlayerDisplayRefreshService playerDisplayRefreshService,
            GameDataService gameDataService,
            Async async
    ) {
        this.matchTracker = matchTracker;
        this.ladder = miniPvPLadder.ladder();
        this.policy = miniPvPLadder.policy();
        this.sessionService = sessionService;
        this.playerDisplayRefreshService = playerDisplayRefreshService;
        this.gameDataService = gameDataService;
        this.async = async;
    }

    public void onNewRound() {
        settled.set(false);
    }

    public boolean isSettled() {
        return settled.get();
    }

    /**
     * Settles the finished match once. Called on the game thread; storage work runs off it
     * and players are notified back on it.
     *
     * @return completes with {@code true} when ratings were applied
     */
    public CompletableFuture<Boolean> settle(Team winnerTeam) {
        if (winnerTeam == null || winnerTeam == Team.derelict) {
            return CompletableFuture.completedFuture(false);
        }
        if (!settled.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(false); // Already settled
        }

        MiniPvPMatchSnapshot match = matchTracker.snapshot(winnerTeam, System.currentTimeMillis());

        if (match.durationMs() < policy.minimumPlayTimeSeconds() * 1000L) {
            Log.info("MiniPvP match @ finished too quickly (@s); skipping rating settlement",
                    match.matchId(), match.durationMs() / 1000);
            settled.set(false);
            return CompletableFuture.completedFuture(false);
        }

        // Sessions and the map belong to the game thread, so they are read before leaving it.
        Map<String, String> names = new HashMap<>();
        for (String uuid : match.playerUuids()) {
            names.put(uuid, resolvePlayerName(uuid));
        }
        String map = Vars.state != null && Vars.state.map != null ? Vars.state.map.plainName() : null;

        if (match.ratedTeamCount() < 2 || match.ratedPlayers() < policy.minimumPlayers()) {
            Log.info("MiniPvP match @ had insufficient players (@) or teams (@); unrated",
                    match.matchId(), match.ratedPlayers(), match.ratedTeamCount());
            settled.set(false);
            // Kept in the history all the same, so a player can see why it gave no rating.
            CompletableFuture<Boolean> recorded = new CompletableFuture<>();
            async.supply(() -> recordUnrated(match, names, map)).thenMain((ignored, error) -> {
                if (error != null) {
                    Log.err("Failed to record unrated MiniPvP match " + match.matchId(), error);
                }
                recorded.complete(false);
            });
            return recorded;
        }

        CompletableFuture<Boolean> applied = new CompletableFuture<>();
        async.supply(() -> settleMatch(match, names, map)).thenMain((settlement, error) -> {
            if (error != null) {
                Log.err("Failed to settle MiniPvP match " + match.matchId(), error);
                settled.set(false);
                applied.complete(false);
                return;
            }
            if (settlement.result().applied()) {
                announce(settlement);
            }
            applied.complete(settlement.result().applied());
        });
        return applied;
    }

    private SettlementResult recordUnrated(MiniPvPMatchSnapshot match, Map<String, String> names, String map) {
        Instant endedAt = Instant.ofEpochMilli(match.endedAt());
        int season = ladder.seasonAt(endedAt);
        List<MatchParticipant> participants = new ArrayList<>();
        for (MiniPvPMatchSnapshot.TeamResult team : match.teams()) {
            for (MiniPvPMatchSnapshot.Member member : team.members()) {
                participants.add(MatchParticipant.uncounted(member.uuid(), names.getOrDefault(member.uuid(), ""),
                                team.teamId(), team.placement(), team.placement() == 1,
                                ladder.rating(season, member.uuid()), REASON_MATCH_UNRATED)
                        .withParticipation(member.participation()));
            }
        }
        return ladder.settle(season, MatchSettlement.unrated(
                        match.matchId(), TeamEloCalculator.ALGORITHM_VERSION, match.resultHash(), SKIP_NOT_ENOUGH_PLAYERS)
                .withEndedAt(endedAt)
                .withReport(report(match, map, participants)));
    }

    private Settlement settleMatch(MiniPvPMatchSnapshot match, Map<String, String> names, String map) {
        // One season for both the ratings the deltas are calculated from and the standings they
        // are written to: around a rollover the current season is not the one the match counts towards.
        int season = ladder.seasonAt(Instant.ofEpochMilli(match.endedAt()));
        var calculation = calculator.calculate(
                match.ratedTeams(policy, uuid -> ladder.rating(season, uuid)), policy);

        List<StandingMutation> mutations = calculation.deltas().stream()
                .map(delta -> new StandingMutation(delta.uuid(), delta.delta(), delta.placement() == 1))
                .toList();
        List<MatchParticipant> participants = calculation.deltas().stream()
                .map(delta -> participant(delta, names.getOrDefault(delta.uuid(), "")))
                .toList();
        SettlementResult result = ladder.settle(season, MatchSettlement.rated(
                        match.matchId(), calculation.algorithmVersion(), match.resultHash(), mutations)
                .withEndedAt(Instant.ofEpochMilli(match.endedAt()))
                .withReport(report(match, map, participants)));

        if (result.claimed()) {
            recordMatchHistory(match, calculation.deltas(), names);
        }
        return new Settlement(result, calculation.deltas());
    }

    private static MatchReport report(MiniPvPMatchSnapshot match, String map, List<MatchParticipant> participants) {
        return new MatchReport(Instant.ofEpochMilli(match.startedAt()), Instant.ofEpochMilli(match.endedAt()),
                null, map, participants);
    }

    /** A participant of a rated match, with the reason the snapshot's participation rules give. */
    static MatchParticipant participant(TeamEloCalculator.PlayerRatingDelta delta, String name) {
        boolean won = delta.placement() == 1;
        if (delta.participation() <= 0.0) {
            return MatchParticipant.uncounted(delta.uuid(), name, delta.teamId(), delta.placement(), won,
                            delta.oldRating(), won ? REASON_SHORT_PLAY : REASON_LATE_JOIN)
                    .withParticipation(0.0);
        }
        return MatchParticipant.counted(delta.uuid(), name, delta.teamId(), delta.placement(), won,
                        delta.oldRating(), delta.delta(), won ? REASON_WINNER : REASON_DEFEATED)
                .withParticipation(delta.participation());
    }

    private void announce(Settlement settlement) {
        for (TeamEloCalculator.PlayerRatingDelta delta : settlement.deltas()) {
            Session session = sessionService.get(delta.uuid());
            if (session == null || session.data == null) continue;

            notifyPlayer(session, delta);
        }

        if (playerDisplayRefreshService != null) {
            playerDisplayRefreshService.refreshAll();
        }
    }

    private void notifyPlayer(Session session, TeamEloCalculator.PlayerRatingDelta delta) {
        if (session == null || session.locale() == null) return;

        RatingLeague newLeague = RatingLeague.fromRating(delta.newRating());
        String leagueIcon = newLeague.icon();
        String leagueName = session.locale().format(newLeague.localizationKey(), args());

        if (delta.participation() <= 0.0) {
            session.locale().send("pvp-match-settlement-exempt", args());
        } else if (delta.delta() > 0) {
            session.locale().send("pvp-match-settlement-win", args(
                    "oldRating", delta.oldRating(),
                    "newRating", delta.newRating(),
                    "delta", delta.delta(),
                    "leagueIcon", leagueIcon,
                    "leagueName", leagueName
            ));
        } else if (delta.delta() < 0) {
            session.locale().send("pvp-match-settlement-loss", args(
                    "oldRating", delta.oldRating(),
                    "newRating", delta.newRating(),
                    "delta", delta.delta(),
                    "leagueIcon", leagueIcon,
                    "leagueName", leagueName
            ));
        } else {
            session.locale().send("pvp-match-settlement-draw", args(
                    "oldRating", delta.oldRating(),
                    "newRating", delta.newRating(),
                    "delta", delta.delta(),
                    "leagueIcon", leagueIcon,
                    "leagueName", leagueName
            ));
        }
        session.locale().send("pvp-match-settlement-details", args());
    }

    private void recordMatchHistory(
            MiniPvPMatchSnapshot match,
            List<TeamEloCalculator.PlayerRatingDelta> deltas,
            Map<String, String> names
    ) {
        if (gameDataService == null) return;

        List<MatchHistoryRecord.MatchParticipantRecord> participants = deltas.stream()
                .map(delta -> new MatchHistoryRecord.MatchParticipantRecord(
                        delta.uuid(),
                        names.getOrDefault(delta.uuid(), "Unknown"),
                        delta.placement(),
                        delta.placement() == 1,
                        delta.participation() >= 0.5))
                .toList();

        String winnerUuid = participants.stream()
                .filter(MatchHistoryRecord.MatchParticipantRecord::winner)
                .map(MatchHistoryRecord.MatchParticipantRecord::uuid)
                .findFirst()
                .orElse(null);

        MatchHistoryRecord record = new MatchHistoryRecord(
                match.matchId(),
                "minipvp",
                "1.0",
                match.startedAt(),
                match.endedAt(),
                "NATURAL",
                winnerUuid,
                participants,
                true
        );

        gameDataService.recordMatch(record);
    }

    private String resolvePlayerName(String uuid) {
        Session session = sessionService.get(uuid);
        if (session != null && session.data != null && session.data.nickname != null) {
            return session.data.nickname;
        }
        // A player who has left is named as they joined the match.
        MiniPvPMatchTracker.ParticipantInfo info = matchTracker.participants().get(uuid);
        return info != null && info.plainName() != null ? info.plainName() : "Unknown";
    }

    private record Settlement(SettlementResult result, List<TeamEloCalculator.PlayerRatingDelta> deltas) {
    }
}
