package org.xcore.ui.runtime;

/**
 * The way back from an {@link Effect} into its session.
 *
 * <p>A dispatcher stops delivering once its session is closed or replaced, and a keyed one also
 * once a newer effect of the same key has been started, so a late answer never lands on a dialog
 * that has moved on. Call it from the thread that runs the session (the server's main thread).
 *
 * @param <E> the event type of the controller
 */
public interface Dispatcher<E> {

    /** Hands {@code event} to the reducer if this dispatcher is still current. */
    boolean dispatch(E event);

    /** Whether {@link #dispatch} would still deliver. */
    boolean isCurrent();
}
