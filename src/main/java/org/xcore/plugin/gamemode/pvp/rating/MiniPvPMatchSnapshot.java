package org.xcore.plugin.gamemode.pvp.rating;

import org.xcore.plugin.rating.RatingPolicy;
import org.xcore.plugin.rating.ladder.MatchSettlement;
import org.xcore.plugin.rating.math.TeamEloCalculator;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Immutable picture of a finished MiniPvP match, taken on the game thread so the rating
 * settlement can run off it.
 */
public record MiniPvPMatchSnapshot(String matchId, long startedAt, long endedAt, List<TeamResult> teams) {
    /** Why the match did or did not change one participant's rating; the match history words them. */
    public static final String REASON_WINNER = "winner";
    public static final String REASON_DEFEATED = "defeated";
    /** On the winning side for less than half of the match. */
    public static final String REASON_SHORT_PLAY = "short_play";
    /** Joined a side that had already lost, at the very end. */
    public static final String REASON_LATE_JOIN = "late_join";

    /**
     * @param participation share of the match that counts for rating, 0.0 for an exempt player
     * @param reason        why that share was given, decided together with it
     */
    public record Member(String uuid, double participation, String reason) {
    }

    public record TeamResult(int teamId, int placement, List<Member> members) {
        public TeamResult {
            members = List.copyOf(members);
        }
    }

    public MiniPvPMatchSnapshot {
        teams = List.copyOf(teams);
    }

    public long durationMs() {
        return endedAt - startedAt;
    }

    public int totalPlayers() {
        return teams.stream().mapToInt(team -> team.members().size()).sum();
    }

    public long ratedPlayers() {
        return teams.stream().flatMap(team -> team.members().stream())
                .filter(member -> member.participation() > 0.0).count();
    }

    public long ratedTeamCount() {
        return teams.stream().filter(team -> team.members().stream()
                .anyMatch(member -> member.participation() > 0.0)).count();
    }

    public List<String> playerUuids() {
        return teams.stream().flatMap(team -> team.members().stream()).map(Member::uuid).toList();
    }

    public List<TeamEloCalculator.RatedTeam> ratedTeams(RatingPolicy policy, ToIntFunction<String> ratingResolver) {
        return teams.stream()
                .map(team -> new TeamEloCalculator.RatedTeam(team.teamId(), team.placement(), team.members().stream()
                        .map(member -> new TeamEloCalculator.RatedMember(
                                member.uuid(),
                                Math.max(policy.minimumRating(), ratingResolver.applyAsInt(member.uuid())),
                                member.participation()))
                        .toList()))
                .toList();
    }

    /**
     * Fingerprint of the result: who was on which team and how the teams placed. It leaves out
     * the end time and everything derived from it, so settling the same match again matches.
     */
    public String resultHash() {
        String canonical = matchId + "|" + startedAt + "|" + teams.stream()
                .sorted(Comparator.comparingInt(TeamResult::teamId))
                .map(team -> team.teamId() + ":" + team.placement() + ":" + team.members().stream()
                        .map(Member::uuid)
                        .sorted()
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
        return MatchSettlement.hash(canonical);
    }
}
