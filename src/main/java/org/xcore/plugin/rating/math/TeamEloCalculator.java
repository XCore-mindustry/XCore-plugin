package org.xcore.plugin.rating.math;

import org.xcore.plugin.rating.RatingPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure, deterministic zero-sum Elo calculator for team-based PvP.
 * Supports asymmetric team sizes and multi-team placement rankings.
 */
public final class TeamEloCalculator {
    public static final String ALGORITHM_VERSION = "team-elo-v2";

    public record RatedMember(String uuid, int rating, double participation) {
        public RatedMember {
            if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid must not be blank");
            if (rating < 0) throw new IllegalArgumentException("rating must not be negative");
            if (participation < 0.0 || participation > 1.0) {
                throw new IllegalArgumentException("participation must be between 0.0 and 1.0");
            }
        }

        public RatedMember(String uuid, int rating) {
            this(uuid, rating, 1.0);
        }
    }

    public record RatedTeam(int teamId, int placement, List<RatedMember> members) {
        public RatedTeam {
            if (placement < 1) throw new IllegalArgumentException("placement must be positive");
            members = List.copyOf(Objects.requireNonNull(members, "members"));
            if (members.isEmpty()) throw new IllegalArgumentException("team must have members");
        }

        /** Exempt members remain in the result but never affect team strength or shares. */
        public long ratedMemberCount() {
            return members.stream().filter(member -> member.participation() > 0.0).count();
        }

        public double averageRating() {
            return members.stream().filter(member -> member.participation() > 0.0)
                    .mapToInt(RatedMember::rating).average().orElse(1000.0);
        }

        public double effectiveRating() {
            return EloMath.effectiveTeamRating(averageRating(), (int) ratedMemberCount());
        }
    }

    public record PlayerRatingDelta(
            String uuid,
            int oldRating,
            int delta,
            int newRating,
            int placement,
            int teamId,
            double participation
    ) {}

    public record TeamRatingCalculation(List<PlayerRatingDelta> deltas, String algorithmVersion) {
        public TeamRatingCalculation {
            deltas = List.copyOf(deltas);
            Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        }
    }

    public TeamRatingCalculation calculate(List<RatedTeam> inputTeams, RatingPolicy policy) {
        Objects.requireNonNull(inputTeams, "inputTeams");
        Objects.requireNonNull(policy, "policy");

        if (inputTeams.size() < 2) {
            throw new IllegalArgumentException("At least two teams are required for rating calculation");
        }

        var teams = new ArrayList<>(inputTeams);
        teams.sort(Comparator.comparingInt(RatedTeam::teamId));

        int totalPlayers = teams.stream().mapToInt(t -> t.members().size()).sum();
        long ratedPlayers = teams.stream().mapToLong(RatedTeam::ratedMemberCount).sum();
        if (ratedPlayers < policy.minimumPlayers()) {
            throw new IllegalArgumentException("Not enough players for a rated team match");
        }

        long distinctUuids = teams.stream()
                .flatMap(t -> t.members().stream())
                .map(RatedMember::uuid)
                .distinct()
                .count();
        if (distinctUuids != totalPlayers) {
            throw new IllegalArgumentException("Duplicate player UUID across match participants");
        }

        var ratedTeams = teams.stream().filter(team -> team.ratedMemberCount() > 0).toList();
        if (ratedTeams.size() < 2) {
            throw new IllegalArgumentException("At least two teams with rated participants are required");
        }
        int n = ratedTeams.size();
        List<PlayerRatingDelta> deltas = new ArrayList<>();

        for (RatedTeam team : teams) {
            long ratedMemberCount = team.ratedMemberCount();
            double teamPoolDelta = 0.0;

            for (RatedTeam opponent : ratedTeams) {
                if (ratedMemberCount == 0 || team.teamId() == opponent.teamId()) continue;

                double actualScore = team.placement() < opponent.placement() ? 1.0
                        : (team.placement() == opponent.placement() ? 0.5 : 0.0);
                double expectedScore = EloMath.expectedScore(team.effectiveRating(), opponent.effectiveRating());

                teamPoolDelta += policy.kFactor() * (actualScore - expectedScore);
            }

            // Normalize across (N - 1) opponent teams
            double normalizedPool = teamPoolDelta / (n - 1);
            double baseMemberShare = ratedMemberCount == 0 ? 0.0 : normalizedPool / ratedMemberCount;

            for (RatedMember member : team.members()) {
                int delta;
                if (member.participation() <= 0.0) {
                    delta = 0;
                } else if (baseMemberShare >= 0.0) {
                    // Win or rating gain: prorated by participation
                    delta = (int) Math.round(baseMemberShare * member.participation());
                } else {
                    // Loss: full penalty unless completely exempt (prevents disconnect dodging)
                    delta = (int) Math.round(baseMemberShare);
                }

                int newRating = Math.max(policy.minimumRating(), member.rating() + delta);
                deltas.add(new PlayerRatingDelta(
                        member.uuid(),
                        member.rating(),
                        delta,
                        newRating,
                        team.placement(),
                        team.teamId(),
                        member.participation()
                ));
            }
        }

        deltas.sort(Comparator.comparing(PlayerRatingDelta::uuid));
        return new TeamRatingCalculation(Collections.unmodifiableList(deltas), ALGORITHM_VERSION);
    }
}
