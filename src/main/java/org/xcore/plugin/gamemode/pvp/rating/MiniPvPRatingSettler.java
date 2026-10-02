package org.xcore.plugin.gamemode.pvp.rating;

import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.integration.gamehistory.MatchHistoryRecord;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.math.TeamEloCalculator;
import org.xcore.plugin.service.GameDataService;
import org.xcore.plugin.service.TopMenuCacheService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class MiniPvPRatingSettler {

    private final MiniPvPMatchTracker matchTracker;
    private final TeamEloCalculator calculator = new TeamEloCalculator();
    private final RatingPolicy policy = RatingPolicy.teamEloV1();

    private final SessionService sessionService;
    private final PlayerDataRepository playerDataRepository;
    private final TopMenuCacheService topMenuCacheService;
    private final PlayerDisplayRefreshService playerDisplayRefreshService;
    private final GameDataService gameDataService;
    private final Async async;

    private final AtomicBoolean settled = new AtomicBoolean(false);

    @Inject
    public MiniPvPRatingSettler(
            MiniPvPMatchTracker matchTracker,
            SessionService sessionService,
            PlayerDataRepository playerDataRepository,
            TopMenuCacheService topMenuCacheService,
            PlayerDisplayRefreshService playerDisplayRefreshService,
            GameDataService gameDataService,
            Async async
    ) {
        this.matchTracker = matchTracker;
        this.sessionService = sessionService;
        this.playerDataRepository = playerDataRepository;
        this.topMenuCacheService = topMenuCacheService;
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

    public boolean settle(Team winnerTeam) {
        if (winnerTeam == null || winnerTeam == Team.derelict) {
            return false;
        }
        if (!settled.compareAndSet(false, true)) {
            return false; // Already settled
        }

        long endedAt = System.currentTimeMillis();
        long startedAt = matchTracker.startedAt();
        long durationMs = endedAt - startedAt;

        if (durationMs < policy.minimumPlayTimeSeconds() * 1000L) {
            Log.info("MiniPvP match @ finished too quickly (@s); skipping rating settlement",
                    matchTracker.matchId(), durationMs / 1000);
            settled.set(false);
            return false;
        }

        List<TeamEloCalculator.RatedTeam> ratedTeams = matchTracker.buildRatedTeams(
                winnerTeam,
                endedAt,
                policy,
                this::resolvePlayerRating
        );

        int totalPlayers = ratedTeams.stream().mapToInt(t -> t.members().size()).sum();
        if (ratedTeams.size() < 2 || totalPlayers < policy.minimumPlayers()) {
            Log.info("MiniPvP match @ had insufficient players (@) or teams (@); unrated",
                    matchTracker.matchId(), totalPlayers, ratedTeams.size());
            settled.set(false);
            return false;
        }

        TeamEloCalculator.TeamRatingCalculation calculation;
        try {
            calculation = calculator.calculate(ratedTeams, policy);
        } catch (Exception e) {
            Log.err("Failed to calculate MiniPvP team ratings: @", e.getMessage());
            settled.set(false);
            return false;
        }

        List<MatchHistoryRecord.MatchParticipantRecord> historyParticipants = new ArrayList<>();

        for (TeamEloCalculator.PlayerRatingDelta delta : calculation.deltas()) {
            boolean isWinner = (delta.placement() == 1);
            boolean playedToEnd = (delta.participation() >= 0.5);

            historyParticipants.add(new MatchHistoryRecord.MatchParticipantRecord(
                    delta.uuid(),
                    resolvePlayerName(delta.uuid()),
                    delta.placement(),
                    isWinner,
                    playedToEnd
            ));

            applyRatingDelta(delta, isWinner);
        }

        recordMatchHistory(winnerTeam, historyParticipants, startedAt, endedAt);

        if (topMenuCacheService != null) {
            topMenuCacheService.invalidateAllAsync();
        }
        if (playerDisplayRefreshService != null) {
            playerDisplayRefreshService.refreshAll();
        }

        return true;
    }

    private void applyRatingDelta(TeamEloCalculator.PlayerRatingDelta delta, boolean isWinner) {
        String uuid = delta.uuid();
        int newRating = delta.newRating();

        Session session = sessionService.get(uuid);
        if (session != null && session.data != null) {
            session.data.pvpRating = newRating;
            session.data.pvpMatches++;
            if (isWinner) {
                session.data.pvpWins++;
            }

            notifyPlayer(session, delta);
        }

        if (playerDataRepository != null && async != null) {
            async.observe(playerDataRepository.updatePvpRatingAndStatsAsync(uuid, newRating, isWinner), (saved, err) -> {
                if (err != null) {
                    Log.warn("Failed to persist MiniPvP rating update for @: @", uuid, err.getMessage());
                }
            });
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
            Team winnerTeam,
            List<MatchHistoryRecord.MatchParticipantRecord> participants,
            long startedAt,
            long endedAt
    ) {
        if (gameDataService == null) return;

        String winnerUuid = participants.stream()
                .filter(MatchHistoryRecord.MatchParticipantRecord::winner)
                .map(MatchHistoryRecord.MatchParticipantRecord::uuid)
                .findFirst()
                .orElse(null);

        MatchHistoryRecord record = new MatchHistoryRecord(
                matchTracker.matchId(),
                "minipvp",
                "1.0",
                startedAt,
                endedAt,
                "NATURAL",
                winnerUuid,
                participants,
                true
        );

        gameDataService.recordMatch(record);
    }

    private int resolvePlayerRating(String uuid) {
        Session session = sessionService.get(uuid);
        if (session != null && session.data != null && session.data.pvpRating > 0) {
            return session.data.pvpRating;
        }

        if (playerDataRepository != null) {
            PlayerData data = playerDataRepository.findByUuid(uuid);
            if (data != null && data.pvpRating > 0) {
                return data.pvpRating;
            }
        }

        return policy.defaultRating();
    }

    private String resolvePlayerName(String uuid) {
        Session session = sessionService.get(uuid);
        if (session != null && session.data != null && session.data.nickname != null) {
            return session.data.nickname;
        }
        return "Unknown";
    }
}
