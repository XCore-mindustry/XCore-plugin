package org.xcore.plugin.rating.season;

import java.util.List;
import java.util.Optional;

/**
 * Persistence of seasons, shared by every server on the network.
 *
 * <p>Every method blocks on storage; never call it from the Mindustry main thread.</p>
 */
public interface SeasonStore {

    Optional<Season> find(String ladderId, int number);

    /** Every season of a ladder, newest first. */
    List<Season> list(String ladderId);

    /** Every season of every ladder, by ladder and then newest first. */
    List<Season> all();

    /** The seasons the lifecycle still has work to do on: active and closing ones. */
    List<Season> open();

    /** @return {@code false} when the season already exists, in which case nothing is written */
    boolean create(Season season);

    /**
     * Stores {@code updated} if the season is still at {@code expected}'s revision.
     * This is how servers race for a transition: exactly one of them gets a result.
     *
     * @return the stored season with its new revision, empty when another change got there first
     */
    Optional<Season> update(Season expected, Season updated);

    /** Counts one more rated match towards a season. Does not disturb concurrent {@link #update}s. */
    void countMatch(String ladderId, int number);
}
