package org.xcore.plugin.rating.ladder;

import com.mongodb.client.ClientSession;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * Persistence of ladder standings, keyed by {@code (ladder, season, player)}.
 *
 * <p>Every method blocks on storage; never call it from the Mindustry main thread.</p>
 */
public interface LadderStore {
    int MAX_PAGE_SIZE = 100;

    Optional<LadderStanding> find(String ladderId, int season, String uuid);

    /**
     * Applies a match result to a standing at most once per operation, creating the
     * standing from {@code startingRating} when the player has none in this season.
     */
    ApplyResult applyOnce(String ladderId, int season, String operationId, StandingMutation mutation,
                          int startingRating, int minimumRating);

    StandingPage top(String ladderId, int season, int limit, @Nullable String cursor);

    /** 1-based leaderboard position, empty when the player has no standing in this season. */
    OptionalLong rankOf(String ladderId, int season, String uuid);

    long count(String ladderId, int season);

    /**
     * Folds every standing of {@code sourceUuid} into {@code targetUuid}: the higher rating
     * wins, counters are summed.
     *
     * @param session transaction to join, or {@code null} to run without one
     * @return number of source standings merged
     */
    int mergePlayer(@Nullable ClientSession session, String sourceUuid, String targetUuid);

    /**
     * @param applied  {@code false} when the operation had already been applied to this player
     * @param standing the standing after the call
     */
    record ApplyResult(boolean applied, LadderStanding standing) {
    }
}
