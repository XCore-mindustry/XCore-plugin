package org.xcore.ui.runtime;

import mindustry.ui.builder.MenuResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Maps the action ids of a dialog's buttons to its events, for {@link UiController#parseEvent}.
 *
 * <pre>{@code
 * private static final ActionRouter<HelpEvent> ACTIONS = ActionRouter.<HelpEvent>create()
 *         .on("action:back", BackToList::new)
 *         .onResult("action:search", r -> new Search(r.getString("field_search", "")))
 *         .prefixInt("action:page:", OpenPage::new)
 *         .prefix("action:cmd:", SelectCommand::new);
 *
 * public HelpEvent parseEvent(MenuResult result) {
 *     return ACTIONS.parse(result);
 * }
 * }</pre>
 *
 * <p>An exact action wins over a prefix; prefixes are tried in the order they were added. An
 * action no route knows, a malformed number, or a mapper that returns {@code null} parses to
 * {@code null}, which the session ignores: a stray action id leaves the dialog as it is.
 * Routers are immutable once built and safe to share.
 *
 * @param <E> the event type of the controller
 */
public final class ActionRouter<E> {

    private final Map<String, Function<MenuResult, ? extends E>> exact;
    private final List<Prefix<E>> prefixes;

    private record Prefix<E>(String prefix, BiFunction<String, MenuResult, ? extends E> mapper) {
    }

    private ActionRouter(Map<String, Function<MenuResult, ? extends E>> exact, List<Prefix<E>> prefixes) {
        this.exact = exact;
        this.prefixes = prefixes;
    }

    public static <E> ActionRouter<E> create() {
        return new ActionRouter<>(Map.of(), List.of());
    }

    /** {@code action} maps to the event {@code event} makes. */
    public ActionRouter<E> on(String action, Supplier<? extends E> event) {
        Objects.requireNonNull(event, "event");
        return onResult(action, result -> event.get());
    }

    /** {@code action} maps to an event made from the whole result, such as the fields it carries. */
    public ActionRouter<E> onResult(String action, Function<MenuResult, ? extends E> event) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(event, "event");
        Map<String, Function<MenuResult, ? extends E>> next = new HashMap<>(exact);
        if (next.put(action, event) != null) {
            throw new IllegalArgumentException("Duplicate action: " + action);
        }
        return new ActionRouter<>(Map.copyOf(next), prefixes);
    }

    /** An action starting with {@code prefix} maps to an event made from the rest of it. */
    public ActionRouter<E> prefix(String prefix, Function<String, ? extends E> event) {
        Objects.requireNonNull(event, "event");
        return prefixResult(prefix, (rest, result) -> event.apply(rest));
    }

    /** An action starting with {@code prefix} maps to an event made from the rest of it and the result. */
    public ActionRouter<E> prefixResult(String prefix, BiFunction<String, MenuResult, ? extends E> event) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(event, "event");
        List<Prefix<E>> next = new ArrayList<>(prefixes);
        next.add(new Prefix<>(prefix, event));
        return new ActionRouter<>(exact, List.copyOf(next));
    }

    /** An action starting with {@code prefix} and ending in a whole number maps to an event made from it. */
    public ActionRouter<E> prefixInt(String prefix, IntFunction<? extends E> event) {
        Objects.requireNonNull(event, "event");
        return prefix(prefix, rest -> {
            try {
                return event.apply(Integer.parseInt(rest));
            } catch (NumberFormatException ignored) {
                return null;
            }
        });
    }

    /** The event of {@code result}'s action, or {@code null} for a cancel or an action no route knows. */
    public E parse(MenuResult result) {
        if (result == null || result.wasCancelled()) {
            return null;
        }
        String action = result.result.trim();
        Function<MenuResult, ? extends E> mapper = exact.get(action);
        if (mapper != null) {
            return mapper.apply(result);
        }
        for (Prefix<E> prefix : prefixes) {
            if (action.startsWith(prefix.prefix())) {
                return prefix.mapper().apply(action.substring(prefix.prefix().length()), result);
            }
        }
        return null;
    }
}
