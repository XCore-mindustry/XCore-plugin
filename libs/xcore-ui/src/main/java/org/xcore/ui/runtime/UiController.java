package org.xcore.ui.runtime;

import org.xcore.ui.VNode;

/**
 * Unidirectional (Elm/MVI) screen controller.
 *
 * <p>A controller owns two pure artifacts; the starting model is passed to {@link UiSession#start}:
 * <ul>
 *   <li>{@link #update(Object, Object, ControllerContext)}: a pure reducer
 *       mapping (model, event) to a new model plus render directives;</li>
 *   <li>{@link #render(Object)}: a pure view function model -> VNode.</li>
 * </ul>
 *
 * <p>A reducer must not perform I/O itself: it returns it as an {@link Effect} on the
 * {@link UpdateResult}, which the session runs once the new model is in place.
 *
 * <p>Events are usually records parsed from {@code MenuResult}: a button click
 * arrives as {@code result}, a form submit carries the field map. A cancelled result (the player
 * closed the dialog) never reaches {@link #parseEvent}; it goes to {@link #closed} instead.
 *
 * <p>The view must not branch on the player's device. The server cannot know it —
 * {@code ConnectPacket} carries only {@code mobile}. Nor does the client shrink a dialog to its
 * screen: a wrapped label asks for the width of its whole line and a scroll pane grants it, so a
 * cap such as {@code growX() + maxWidth} does not keep the content on a phone's screen. Lay the
 * dialog out with a width on every piece, once per class of screens, and let the client build
 * the one for its own screen with {@link org.xcore.ui.responsive.Variants#byWidth}.
 *
 * @param <Model> immutable state record
 * @param <Event> sealed event type
 */
public interface UiController<Model, Event> {

    /**
     * Factory for the initial model of a new dialog session.
     *
     * @deprecated {@link UiSession#start} takes the initial model itself and never calls this;
     *             build the model where the session is started.
     */
    @Deprecated(forRemoval = true)
    default Model initialModel(Object context) {
        throw new UnsupportedOperationException("initialModel is not used; pass the model to UiSession.start");
    }

    /** Pure reducer. Must not perform I/O directly; use the returned directives instead. */
    UpdateResult<Model> update(Model model, Event event, ControllerContext ctx);

    /**
     * Pure view function. The returned tree must contain slots matching any keys you patch.
     * Implement this one, or {@link #render(Object, RenderContext)} when the view needs the words
     * themselves, such as to measure them.
     */
    default VNode render(Model model) {
        throw new UnsupportedOperationException("implement render(Model) or render(Model, RenderContext)");
    }

    /** The view with what else it may know; this is what the session calls. */
    default VNode render(Model model, RenderContext context) {
        return render(model);
    }

    /**
     * The player closed the dialog on the client. The session has ended and there is nothing to
     * hide; release what the dialog held (subscriptions, pending loads).
     */
    default void closed(Model model) {
    }

    /** Parses a raw {@code MenuResult} into a typed event, or null if unhandled. */
    default Event parseEvent(mindustry.ui.builder.MenuResult result) {
        return null;
    }
}
