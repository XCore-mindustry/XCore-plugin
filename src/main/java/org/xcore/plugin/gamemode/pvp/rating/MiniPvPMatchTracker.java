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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Singleton
public class MiniPvPMatchTracker {

    public record ParticipantInfo(
            String uuid,
            String plainName,
            int teamId,
            long joinTime,
            long leaveTime
    ) {
        public ParticipantInfo withLeaveTime(long newLeaveTime) {
            return new ParticipantInfo(uuid, plainName, teamId, joinTime, newLeaveTime);
        }

        public ParticipantInfo withTeamId(int newTeamId) {
            return new ParticipantInfo(uuid, plainName, newTeamId, joinTime, leaveTime);
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
            // Reconnected player: clear leave time, but keep their original assigned team for rating integrity
            return existing.withLeaveTime(0L);
        });
    }

    public void onPlayerLeave(Player player) {
        if (player == null || player.uuid() == null) return;
        long now = System.currentTimeMillis();

        participants.computeIfPresent(player.uuid(), (uuid, existing) -> existing.withLeaveTime(now));
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

        long matchDurationMs = Math.max(1000L, endedAt - startedAt);
        long minPlayTimeMs = policy.minimumPlayTimeSeconds() * 1000L;

        Map<Integer, List<TeamEloCalculator.RatedMember>> membersByTeam = new ConcurrentHashMap<>();

        for (ParticipantInfo p : participants.values()) {
            if (p.teamId() == Team.derelict.id || p.teamId() == 255) continue;

            int teamPlacement = getTeamPlacement(p.teamId(), winnerTeam);
            boolean isWinner = (teamPlacement == 1);

            long effectiveEnd = (p.leaveTime() > 0 && p.leaveTime() < endedAt) ? p.leaveTime() : endedAt;
            long activePlayTimeMs = Math.max(0L, effectiveEnd - p.joinTime());
            double rawParticipation = (double) activePlayTimeMs / matchDurationMs;
            rawParticipation = Math.clamp(rawParticipation, 0.0, 1.0);

            boolean presentAtStart = (p.joinTime() - startedAt <= 20_000L);

            double effectiveParticipation;
            if (!isWinner) {
                // On losing team:
                // If player was present at the match start, played >= 25% of the round, or stayed for >= 15s:
                // Full loss penalty (strictly prevents disconnect-dodging right before core destruction!)
                if (presentAtStart || rawParticipation >= 0.25 || activePlayTimeMs >= 15_000L) {
                    effectiveParticipation = 1.0;
                } else {
                    // Truly joined at the very end on an already defeated team: exempt
                    effectiveParticipation = 0.0;
                }
            } else {
                // On winning team:
                // Must have stayed for >= 50% of the match to gain rating (no free carry gain for late joiners)
                effectiveParticipation = rawParticipation >= 0.5 ? rawParticipation : 0.0;
            }

            int rating = Math.max(policy.minimumRating(), ratingResolver.apply(p.uuid()));
            TeamEloCalculator.RatedMember member = new TeamEloCalculator.RatedMember(
                    p.uuid(), rating, effectiveParticipation);

            membersByTeam.computeIfAbsent(p.teamId(), k -> new ArrayList<>()).add(member);
        }

        List<TeamEloCalculator.RatedTeam> ratedTeams = new ArrayList<>();
        for (var entry : membersByTeam.entrySet()) {
            int teamId = entry.getKey();
            List<TeamEloCalculator.RatedMember> members = entry.getValue();
            if (!members.isEmpty()) {
                int placement = getTeamPlacement(teamId, winnerTeam);
                ratedTeams.add(new TeamEloCalculator.RatedTeam(teamId, placement, members));
            }
        }

        return ratedTeams;
    }
}
