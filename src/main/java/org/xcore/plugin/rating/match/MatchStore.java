package org.xcore.plugin.rating.match;

import com.mongodb.client.ClientSession;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

/**
 * The history of settled matches, one record per match and ladder.
 *
 * <p>Every method blocks on storage; never call it from the Mindustry main thread.</p>
 */
public interface MatchStore {
    int MAX_PAGE_SIZE = 50;

    /** Stores {@code match}, replacing an earlier record of the same match. */
    void record(MatchRecord match);

    /** The player's matches on a ladder, newest first, each with the player's own entry only. */
    MatchPage page(String ladderId, String uuid, int limit, @Nullable String cursor);

    /** Number of the player's matches on a ladder. */
    long count(String ladderId, String uuid);

    /** A whole match, with every participant. */
    Optional<MatchRecord> find(String ladderId, String matchId);

    /** When the oldest match the ladder's history holds ended: the history starts there. */
    Optional<Instant> firstRecorded(String ladderId);

    /**
     * Moves every match of {@code sourceUuid} to {@code targetUuid}.
     *
     * @param session transaction to join, or {@code null} to run without one
     * @return number of matches changed
     */
    long reassignPlayer(@Nullable ClientSession session, String sourceUuid, String targetUuid);
}
