package org.xcore.plugin.rating.season;

import java.time.Duration;
import java.time.Instant;

/**
 * A "season ends soon" announcement point.
 *
 * @param key  the threshold as configured (for example {@code 7d}); identifies the notice in storage
 * @param lead how long before the season's end the notice becomes due
 */
public record SeasonNotice(String key, Duration lead) {

    public boolean dueAt(Instant now, Instant seasonEnd) {
        return !now.isBefore(seasonEnd.minus(lead));
    }
}
