package org.xcore.plugin.gamemode.pvp.rating;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.integration.gamehistory.MatchHistoryRecord;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.ladder.MatchSettlement;
import org.xcore.plugin.rating.ladder.SettlementResult;
import org.xcore.plugin.rating.ladder.StandingMutation;
import org.xcore.plugin.rating.math.TeamEloCalculator;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class MiniPvPRatingSettler {

    private final MiniPvPMatchTracker matchTracker;
    private final TeamEloCalculator calculator = new TeamEloCalculator();
    private final Ladder ladder;
    private final RatingPolicy policy;
    private final LegacyPvpRatingMirror legacyMirror;

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
            PlayerDataRepository playerDataRepository,
            PlayerDisplayRefreshService playerDisplayRefreshService,
            GameDataService gameDataService,
            Async async
    ) {
        this.matchTracker = matchTracker;
        this.ladder = miniPvPLadder.ladder();
        this.policy = miniPvPLadder.policy();
        this.legacyMirror = new LegacyPvpRatingMirror(playerDataRepository);
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

        if (match.teams().size() < 2 || match.totalPlayers() < policy.minimumPlayers()) {
            Log.info("MiniPvP match @ had insufficient players (@) or teams (@); unrated",
                    match.matchId(), match.totalPlayers(), match.teams().size());
            settled.set(false);
            return CompletableFuture.completedFuture(false);
        }

        // Sessions belong to the game thread, so names are read before leaving it.
        Map<String, String> names = new HashMap<>();
        for (String uuid : match.playerUuids()) {
            names.put(uuid, resolvePlayerName(uuid));
        }

        CompletableFuture<Boolean> applied = new CompletableFuture<>();
        async.supply(() -> settleMatch(match, names)).thenMain((settlement, error) -> {
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

    private Settlement settleMatch(MiniPvPMatchSnapshot match, Map<String, String> names) {
        var calculation = calculator.calculate(match.ratedTeams(policy, ladder::rating), policy);

        List<StandingMutation> mutations = calculation.deltas().stream()
                .map(delta -> new StandingMutation(delta.uuid(), delta.delta(), delta.placement() == 1))
                .toList();
        SettlementResult result = ladder.settle(MatchSettlement.rated(
                match.matchId(), calculation.algorithmVersion(), match.resultHash(), mutations));

        if (result.claimed()) {
            recordMatchHistory(match, calculation.deltas(), names);
        }
        if (result.applied()) {
            legacyMirror.persist(result.standings().values());
        }
        return new Settlement(result, calculation.deltas());
    }

    private void announce(Settlement settlement) {
        for (TeamEloCalculator.PlayerRatingDelta delta : settlement.deltas()) {
            Session session = sessionService.get(delta.uuid());
            if (session == null || session.data == null) continue;

            LadderStanding standing = settlement.result().standings().get(delta.uuid());
            if (standing != null) {
                LegacyPvpRatingMirror.apply(session.data, standing);
            }
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
        return "Unknown";
    }

    private record Settlement(SettlementResult result, List<TeamEloCalculator.PlayerRatingDelta> deltas) {
    }
}
