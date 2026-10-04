package org.xcore.plugin.rating.season;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.model.AuditActor;

/**
 * Hears about each season transition exactly once on the whole network, from the server that
 * won the conditional write behind it. Where {@link SeasonObserver} tells every server what
 * changed for its own players, this is for effects that must happen once per change, such as
 * telling other services.
 *
 * <p>Called on the thread that advanced the season, never the Mindustry main thread, and a
 * failure in one listener does not stop the others or the season. Delivery of {@link #started} and {@link #ended}
 * is at least once: a server that dies right after winning the write, or a listener that
 * throws, is covered by the lifecycle's reconcile pass. A listener must therefore throw from
 * those two when it could not pass the event on. The other events are at most once.</p>
 */
public interface SeasonEvents {

    /**
     * A new season began; {@code previous} is the one it replaced, or {@code null} for the first
     * season of a ladder. May be delivered more than once if the first attempt did not finish.
     */
    default void started(@Nullable Season previous, Season season) {
    }

    /** The most urgent of the notices that just became due for a running season. */
    default void noticeDue(Season season, SeasonNotice notice) {
    }

    /** A finished season was archived; its podium and summary are final. May repeat, as {@link #started}. */
    default void ended(Season archived) {
    }

    /** The end of a running season was moved by an administrator. */
    default void rescheduled(Season before, Season after, AuditActor actor, @Nullable String reason) {
    }
}
