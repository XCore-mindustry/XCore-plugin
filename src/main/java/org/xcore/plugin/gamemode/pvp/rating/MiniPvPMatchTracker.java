package org.xcore.plugin.gamemode.pvp.rating;

import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.math.TeamEloCalculator;
import org.xcore.plugin.session.ObserverService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Singleton
public class MiniPvPMatchTracker {
    /** A player who joined within this long of the start played the match from its start. */
    public static final long PRESENT_AT_START_MS = 20_000L;
    /** A loser who played less than this left at once, and loses nothing. */
    public static final long LEFT_AT_ONCE_MS = 15_000L;
    /** A loser who joined late and played less than this share of the match loses nothing. */
    public static final double LATE_JOIN_SHARE = 0.25;
    /** A winner who played less than this share of the match gains nothing. */
    public static final double WINNER_SHARE = 0.5;

    public record ParticipantInfo(
            String uuid,
            String plainName,
            int teamId,
            long joinTime,
            long leaveTime,
            long awayMs
    ) {
        public ParticipantInfo(String uuid, String plainName, int teamId, long joinTime, long leaveTime) {
            this(uuid, plainName, teamId, joinTime, leaveTime, 0L);
        }

        /** Stops the play clock; the first stop wins, so spectating and then leaving is one absence. */
        public ParticipantInfo away(long now) {
            return leaveTime > 0 ? this : new ParticipantInfo(uuid, plainName, teamId, joinTime, now, awayMs);
        }

        /** Resumes the play clock, keeping the time spent away out of the play time. */
        public ParticipantInfo back(long now) {
            return leaveTime == 0 ? this
                    : new ParticipantInfo(uuid, plainName, teamId, joinTime, 0L, awayMs + Math.max(0L, now - leaveTime));
        }
    }

    private volatile String matchId = UUID.randomUUID().toString();
    private volatile long startedAt = System.currentTimeMillis();
    private volatile boolean inProgress = false;

    private final Map<String, ParticipantInfo> participants = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> teamPlacements = new ConcurrentHashMap<>();

    public void startMatch(ObserverService observerService) {
        this.matchId = UUID.randomUUID().toString();
        this.startedAt = System.currentTimeMillis();
        this.inProgress = true;
        this.participants.clear();
        this.teamPlacements.clear();

        if (Groups.player != null) {
            Groups.player.each(p -> {
                if (p != null && (observerService == null || !observerService.isObserving(p))) {
                    onPlayerJoin(p);
                }
            });
        }
    }

    public void reset() {
        this.inProgress = false;
        this.participants.clear();
        this.teamPlacements.clear();
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public String matchId() {
        return matchId;
    }

    public long startedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public void onPlayerJoin(Player player) {
        if (player == null || player.uuid() == null || player.uuid().isBlank()) return;
        long now = System.currentTimeMillis();

        participants.compute(player.uuid(), (uuid, existing) -> {
            if (existing == null) {
                return new ParticipantInfo(uuid, player.plainName(), player.team().id, now, 0L);
            }
            // Reconnected player: keep their original assigned team for rating integrity
            return existing.back(now);
        });
    }

    /** A spectator is away from the match: watching does not count as playing. */
    public void onObserving(Player player, boolean observing) {
        if (player == null || player.uuid() == null) return;
        ParticipantInfo existing = participants.get(player.uuid());
        if (existing == null) {
            // Watched from the start and then picked a side: rated from now on.
            if (!observing && inProgress) onPlayerJoin(player);
            return;
        }
        if (observing == (existing.leaveTime() > 0)) return;
        long now = System.currentTimeMillis();
        participants.computeIfPresent(player.uuid(), (uuid, info) -> observing ? info.away(now) : info.back(now));
    }

    /** The team the player is rated for in the running match, or null. */
    public Team teamOf(String uuid) {
        ParticipantInfo info = inProgress && uuid != null ? participants.get(uuid) : null;
        return info == null ? null : Team.get(info.teamId());
    }

    public void onPlayerLeave(Player player) {
        if (player == null || player.uuid() == null) return;
        long now = System.currentTimeMillis();

        participants.computeIfPresent(player.uuid(), (uuid, existing) -> existing.away(now));
    }

    public void onTeamEliminated(int teamId, int remainingAliveTeams) {
        int placement = Math.max(2, remainingAliveTeams + 1);
        teamPlacements.putIfAbsent(teamId, placement);
    }

    public int getTeamPlacement(int teamId, Team winnerTeam) {
        if (winnerTeam != null && winnerTeam.id == teamId) {
            return 1;
        }
        return teamPlacements.getOrDefault(teamId, 2);
    }

    public void trackParticipant(ParticipantInfo info) {
        if (info != null && info.uuid() != null && !info.uuid().isBlank()) {
            participants.put(info.uuid(), info);
        }
    }

    public Map<String, ParticipantInfo> participants() {
        return Map.copyOf(participants);
    }

    public List<TeamEloCalculator.RatedTeam> buildRatedTeams(
            Team winnerTeam,
            long endedAt,
            RatingPolicy policy,
            Function<String, Integer> ratingResolver
    ) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(ratingResolver, "ratingResolver");
        return snapshot(winnerTeam, endedAt).ratedTeams(policy, ratingResolver::apply);
    }

    /** Freezes the match result: who played on which team, how it placed, and who is exempt. */
    public MiniPvPMatchSnapshot snapshot(Team winnerTeam, long endedAt) {
        long matchDurationMs = Math.max(1000L, endedAt - startedAt);

        Map<Integer, List<MiniPvPMatchSnapshot.Member>> membersByTeam = new TreeMap<>();

        for (ParticipantInfo p : participants.values()) {
            if (p.teamId() == Team.derelict.id || p.teamId() == 255) continue;

            int teamPlacement = getTeamPlacement(p.teamId(), winnerTeam);
            boolean isWinner = (teamPlacement == 1);

            long effectiveEnd = (p.leaveTime() > 0 && p.leaveTime() < endedAt) ? p.leaveTime() : endedAt;
            long activePlayTimeMs = Math.max(0L, effectiveEnd - p.joinTime() - p.awayMs());
            double rawParticipation = (double) activePlayTimeMs / matchDurationMs;
            rawParticipation = Math.clamp(rawParticipation, 0.0, 1.0);

            boolean presentAtStart = (p.joinTime() - startedAt <= PRESENT_AT_START_MS);

            double effectiveParticipation;
            String reason;
            if (!isWinner) {
                // On losing team: leaving or spectating after the first 15s still costs the full loss
                // (no disconnect-dodging before core destruction), unless the player only joined for
                // the last quarter of the match.
                if (!presentAtStart && rawParticipation < LATE_JOIN_SHARE) {
                    effectiveParticipation = 0.0;
                    reason = MiniPvPMatchSnapshot.REASON_LATE_JOIN;
                } else if (activePlayTimeMs < LEFT_AT_ONCE_MS) {
                    effectiveParticipation = 0.0;
                    reason = MiniPvPMatchSnapshot.REASON_LEFT_AT_ONCE;
                } else {
                    effectiveParticipation = 1.0;
                    reason = MiniPvPMatchSnapshot.REASON_DEFEATED;
                }
            } else {
                // On winning team:
                // Must have stayed for >= 50% of the match to gain rating (no free carry gain for late joiners)
                effectiveParticipation = rawParticipation >= WINNER_SHARE ? rawParticipation : 0.0;
                reason = effectiveParticipation > 0.0
                        ? MiniPvPMatchSnapshot.REASON_WINNER
                        : MiniPvPMatchSnapshot.REASON_SHORT_PLAY;
            }

            membersByTeam.computeIfAbsent(p.teamId(), k -> new ArrayList<>())
                    .add(new MiniPvPMatchSnapshot.Member(p.uuid(), effectiveParticipation, reason));
        }

        List<MiniPvPMatchSnapshot.TeamResult> teams = new ArrayList<>();
        for (var entry : membersByTeam.entrySet()) {
            int teamId = entry.getKey();
            teams.add(new MiniPvPMatchSnapshot.TeamResult(teamId, getTeamPlacement(teamId, winnerTeam), entry.getValue()));
        }

        return new MiniPvPMatchSnapshot(matchId, startedAt, endedAt, teams);
    }
}
