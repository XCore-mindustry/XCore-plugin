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
 * failure in one listener does not stop the others or the season. Delivery is at most once:
 * a server that dies right after winning the write does not repeat it.</p>
 */
public interface SeasonEvents {

    /** A new season began; {@code previous} is the one it replaced. */
    default void started(Season previous, Season season) {
    }

    /** The most urgent of the notices that just became due for a running season. */
    default void noticeDue(Season season, SeasonNotice notice) {
    }

    /** A finished season was archived; its podium and summary are final. */
    default void ended(Season archived) {
    }

    /** The end of a running season was moved by an administrator. */
    default void rescheduled(Season before, Season after, AuditActor actor, @Nullable String reason) {
    }
}
