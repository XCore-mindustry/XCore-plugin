package org.xcore.ui.runtime;

import java.util.List;
import java.util.Optional;

/**
 * Result of one {@code UiController.update(...)} transition.
 *
 * <p>Either requests a full re-render ({@link #of}), or a targeted patch of
 * dirty slots ({@link #patch}) which the runtime delivers via
 * {@code Call.menuBuilderUpdate} without re-sending the whole dialog.
 */
public record UpdateResult<Model>(Model model, List<SlotKey<?>> dirtySlots, boolean fullRerender, boolean close) {

    public UpdateResult {
        dirtySlots = dirtySlots == null ? List.of() : List.copyOf(dirtySlots);
    }

    /** Model unchanged, no re-render needed. */
    public static <Model> UpdateResult<Model> of(Model model) {
        return new UpdateResult<>(model, List.of(), false, false);
    }

    /** Model changed; patch only the given slots. */
    @SafeVarargs
    public static <Model> UpdateResult<Model> patch(Model model, SlotKey<?>... slots) {
        return new UpdateResult<>(model, List.of(slots), false, false);
    }

    /** Model changed; patch the given slots. */
    public static <Model> UpdateResult<Model> patch(Model model, List<SlotKey<?>> slots) {
        return new UpdateResult<>(model, slots, false, false);
    }

    /** Model changed; re-render the whole dialog. */
    public static <Model> UpdateResult<Model> rerender(Model model) {
        return new UpdateResult<>(model, List.of(), true, false);
    }

    /** Close the dialog; model is the terminal state. */
    public static <Model> UpdateResult<Model> close(Model model) {
        return new UpdateResult<>(model, List.of(), false, true);
    }

    public boolean isNoop() {
        return !fullRerender && dirtySlots.isEmpty();
    }

    public Optional<Model> modelOpt() {
        return Optional.ofNullable(model);
    }
}
