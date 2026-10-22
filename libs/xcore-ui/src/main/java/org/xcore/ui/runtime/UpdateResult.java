package org.xcore.ui.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of one {@code UiController.update(...)} transition.
 *
 * <p>Either requests a full re-render ({@link #rerender}), or a targeted patch of
 * dirty slots ({@link #patch}) which the runtime delivers via
 * {@code Call.menuBuilderUpdate} without re-sending the whole dialog. Any of them can
 * carry {@link #effect effects}, run once the update is in place.
 */
public record UpdateResult<Model>(Model model, List<SlotKey<?>> dirtySlots, boolean fullRerender, boolean close,
                                  List<Command> effects) {

    public UpdateResult {
        dirtySlots = dirtySlots == null ? List.of() : List.copyOf(dirtySlots);
        effects = effects == null ? List.of() : List.copyOf(effects);
    }

    public UpdateResult(Model model, List<SlotKey<?>> dirtySlots, boolean fullRerender, boolean close) {
        this(model, dirtySlots, fullRerender, close, List.of());
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

    /** This result, plus an effect run once it has been applied. */
    public <E> UpdateResult<Model> effect(Effect<E> effect) {
        return with(new Command(null, effect));
    }

    /**
     * This result, plus an effect that supersedes any earlier effect of the same {@code key}: the
     * dispatcher of the earlier one stops delivering, so only the latest load of a list lands.
     */
    public <E> UpdateResult<Model> effect(String key, Effect<E> effect) {
        return with(new Command(Objects.requireNonNull(key, "key"), effect));
    }

    private UpdateResult<Model> with(Command command) {
        List<Command> all = new ArrayList<>(effects);
        all.add(command);
        return new UpdateResult<>(model, dirtySlots, fullRerender, close, all);
    }

    /** Nothing to send. Effects may still be pending. */
    public boolean isNoop() {
        return !fullRerender && dirtySlots.isEmpty();
    }

    public Optional<Model> modelOpt() {
        return Optional.ofNullable(model);
    }

    /** An effect and the key that orders it against others, {@code null} for none. */
    public record Command(String key, Effect<?> effect) {
        public Command {
            Objects.requireNonNull(effect, "effect");
        }
    }
}
