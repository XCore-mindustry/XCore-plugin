package org.xcore.plugin.rating.math;

import org.xcore.plugin.rating.RatingPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure, deterministic pairwise Elo calculator for multi-player FFA placements.
 */
public final class PlacementEloCalculator {
    public static final String ALGORITHM_VERSION = "placement-elo-v1";

    public record RatedPlayer(String uuid, int rating, int placement) {
        public RatedPlayer {
            if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid must not be blank");
            if (rating < 0) throw new IllegalArgumentException("rating must not be negative");
            if (placement < 1) throw new IllegalArgumentException("placement must be positive");
        }
    }

    public record PlayerRatingDelta(String uuid, int oldRating, int delta, int newRating, int placement) {}

    public record RatingCalculation(List<PlayerRatingDelta> deltas, String algorithmVersion) {
        public RatingCalculation {
            deltas = List.copyOf(deltas);
            Objects.requireNonNull(algorithmVersion, "algorithmVersion");
        }
    }

    public RatingCalculation calculate(List<RatedPlayer> input, RatingPolicy policy) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(policy, "policy");
        if (input.size() < policy.minimumPlayers()) {
            throw new IllegalArgumentException("Not enough players for a rated match");
        }
        var players = new ArrayList<>(input);
        players.sort(Comparator.comparing(RatedPlayer::uuid));
        if (players.stream().map(RatedPlayer::uuid).distinct().count() != players.size()) {
            throw new IllegalArgumentException("Duplicate player UUID");
        }
        var placements = players.stream().map(RatedPlayer::placement).sorted().toList();
        for (int i = 0; i < placements.size(); i++) {
            if (placements.get(i) != i + 1) {
                throw new IllegalArgumentException("Placements must be contiguous starting from 1");
            }
        }

        List<PlayerRatingDelta> deltas = new ArrayList<>();
        for (RatedPlayer player : players) {
            double actual = 0.0;
            double expected = 0.0;
            for (RatedPlayer opponent : players) {
                if (player == opponent) continue;
                actual += player.placement() < opponent.placement() ? 1.0 : 0.0;
                expected += EloMath.expectedScore(player.rating(), opponent.rating());
            }
            int delta = (int) Math.round(policy.kFactor() * (actual - expected) / (players.size() - 1));
            int newRating = Math.max(policy.minimumRating(), player.rating() + delta);
            deltas.add(new PlayerRatingDelta(player.uuid(), player.rating(), delta, newRating, player.placement()));
        }
        return new RatingCalculation(deltas, ALGORITHM_VERSION);
    }
}
