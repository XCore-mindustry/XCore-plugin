package org.xcore.plugin.rating.season;

/**
 * Totals of a finished season.
 *
 * @param participants players with at least one rated match in the season
 * @param matches      rated matches settled into the season
 */
public record SeasonSummary(long participants, int matches) {
}
