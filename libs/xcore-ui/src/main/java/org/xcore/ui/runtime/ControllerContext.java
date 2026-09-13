package org.xcore.ui.runtime;

import org.xcore.ui.VNode;

/**
 * Context passed to {@link UiController#update}. Host implementations expose
 * player-scoped services without the framework depending on them.
 */
public interface ControllerContext {

    /** Player uuid or another stable identifier of the dialog owner. */
    String playerId();

    /** Closes the dialog on the client. */
    void close();

    /** Queues arbitrary follow-up work (e.g. re-opening a parent screen); implementation-defined. */
    default void post(Runnable action) {
        action.run();
    }

    /** Renders arbitrary nodes outside the normal reducer flow; rarely needed. */
    default void override(VNode node) {
        throw new UnsupportedOperationException("override not supported by this context");
    }

    /**
     * Updates the localization strategy for this session and triggers full re-render.
     * Optional operation; host contexts may implement this to allow in-dialog language switching.
     */
    default void updateResolver(org.xcore.ui.LocalizerResolver resolver) {
        // no-op by default
    }
}
