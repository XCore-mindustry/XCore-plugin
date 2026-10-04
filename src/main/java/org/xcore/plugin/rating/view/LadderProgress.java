package org.xcore.plugin.rating.view;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.rating.ladder.LadderDefinition;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.prize.PrizeGrant;
import org.xcore.plugin.rating.season.Season;

import java.util.List;
import java.util.Objects;

/**
 * Where one player stands on a ladder: the running season and the ones before it.
 *
 * @param season   the running season, {@code null} for a ladder without seasons
 * @param standing the player's standing in it; virtual, at the rating they would start
 *                 from, when they have not played a rated match yet
 * @param rank     1-based position in the running season, {@code null} when unplaced
 * @param history  standings in earlier seasons, most recent first
 * @param prizes   the player's prizes on this ladder, newest season first
 */
public record LadderProgress(
        LadderDefinition ladder,
        @Nullable Season season,
        LadderStanding standing,
        @Nullable Long rank,
        List<LadderStanding> history,
        List<PrizeGrant> prizes
) {
    public LadderProgress {
        Objects.requireNonNull(ladder, "ladder");
        Objects.requireNonNull(standing, "standing");
        history = history == null ? List.of() : List.copyOf(history);
        prizes = prizes == null ? List.of() : List.copyOf(prizes);
    }

    public LadderProgress(LadderDefinition ladder, @Nullable Season season, LadderStanding standing,
                          @Nullable Long rank, List<LadderStanding> history) {
        this(ladder, season, standing, rank, history, List.of());
    }

    /** Whether the player has ever played a rated match on this ladder. */
    public boolean everPlayed() {
        return standing.placed() || !history.isEmpty();
    }
}
