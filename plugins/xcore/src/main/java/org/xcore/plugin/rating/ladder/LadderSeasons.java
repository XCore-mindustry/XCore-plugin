package org.xcore.plugin.rating.ladder;

import java.time.Instant;

/**
 * The seasons a ladder's standings are partitioned into. The ladder engine only needs to
 * know which season a match counts towards and what rating a returning player starts from;
 * scheduling, closing and archiving live behind this interface.
 */
public interface LadderSeasons {

    /**
     * Blocking. Called once when a ladder is registered, so its first season exists before
     * anything is settled.
     *
     * @param onSeasonChanged runs off the main thread whenever this server learns that the
     *                        ladder's current season changed
     */
    void open(LadderDefinition definition, Runnable onSeasonChanged);

    /** Never blocks. */
    int current(String ladderId);

    /** Blocking. The season a match that ended at {@code when} counts towards. */
    int at(String ladderId, Instant when);

    /** The rating a player starts a new season with, given the rating they last finished on. */
    int seed(LadderDefinition definition, int previousRating);

    /** Blocking. Called once per rated match applied to {@code season}. */
    void matchSettled(String ladderId, int season);

    /** One open-ended season, for tests and for ladders that never reset. */
    static LadderSeasons single() {
        return new LadderSeasons() {
            @Override
            public void open(LadderDefinition definition, Runnable onSeasonChanged) {
            }

            @Override
            public int current(String ladderId) {
                return Ladder.FIRST_SEASON;
            }

            @Override
            public int at(String ladderId, Instant when) {
                return Ladder.FIRST_SEASON;
            }

            @Override
            public int seed(LadderDefinition definition, int previousRating) {
                return previousRating;
            }

            @Override
            public void matchSettled(String ladderId, int season) {
            }
        };
    }
}
