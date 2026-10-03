package org.xcore.plugin.rating.season;

import java.util.Set;

/** Something this server just learned about a ladder's current season. */
public sealed interface SeasonChange {

    /** The season the change is about. */
    Season season();

    /**
     * A new season is running.
     *
     * @param previous the season it replaced, which is closing or already archived
     */
    record Started(Season previous, Season season) implements SeasonChange {
    }

    /**
     * The season is about to end.
     *
     * @param notices keys of the {@link SeasonNotice}s that became due since the last look
     */
    record NoticeDue(Season season, Set<String> notices) implements SeasonChange {
        public NoticeDue {
            notices = Set.copyOf(notices);
        }
    }
}
