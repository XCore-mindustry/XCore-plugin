package org.xcore.plugin.rating.season;

import java.time.Instant;

/**
 * One change of a season's end date.
 *
 * @param actor who moved it, as {@code type:id} (for example {@code server_console:console})
 */
public record SeasonReschedule(Instant from, Instant to, String actor, Instant at, String reason) {
}
