package org.xcore.ui.runtime;

import org.xcore.ui.VNode;

/**
 * Unidirectional (Elm/MVI) screen controller.
 *
 * <p>A controller owns three pure artifacts:
 * <ul>
 *   <li>{@link #initialModel(Object)}: the starting state for a player;</li>
 *   <li>{@link #update(Object, Object, ControllerContext)}: a pure reducer
 *       mapping (model, event) to a new model plus render directives;</li>
 *   <li>{@link #render(Object)}: a pure view function model -> VNode.</li>
 * </ul>
 *
 * <p>Events are usually records parsed from {@code MenuResult}: a button click
 * arrives as {@code result}, a form submit carries the field map.
 *
 * @param <Model> immutable state record
 * @param <Event> sealed event type
 */
public interface UiController<Model, Event> {

    /** Factory for the initial model of a new dialog session. */
    Model initialModel(Object context);

    /** Pure reducer. Must not perform I/O directly; use the returned directives instead. */
    UpdateResult<Model> update(Model model, Event event, ControllerContext ctx);

    /** Pure view function. The returned tree must contain slots matching any keys you patch. */
    VNode render(Model model);

    /** Parses a raw {@code MenuResult} into a typed event, or null if unhandled. */
    default Event parseEvent(mindustry.ui.builder.MenuResult result) {
        return null;
    }
}
