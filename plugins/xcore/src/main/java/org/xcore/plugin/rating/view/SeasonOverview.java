package org.xcore.plugin.rating.view;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.rating.season.Season;

import java.util.Objects;

/**
 * What {@code /season} shows about one ladder to one player.
 *
 * @param progress     the viewer's own standing and history
 * @param participants players with a standing in the running season
 * @param previous     the season before the running one, {@code null} during the first
 */
public record SeasonOverview(LadderProgress progress, long participants, @Nullable Season previous) {
    public SeasonOverview {
        Objects.requireNonNull(progress, "progress");
    }
}
