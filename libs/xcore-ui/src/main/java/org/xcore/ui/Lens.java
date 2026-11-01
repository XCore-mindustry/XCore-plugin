package org.xcore.ui;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Functional lens giving type-safe bidirectional access to one component of an
 * immutable model record.
 *
 * <pre>{@code
 * public record Settings(boolean chat, float volume) {
 *     public static final Lens<Settings, Float> VOLUME =
 *         Lens.of(Settings::volume, (s, v) -> new Settings(s.chat(), v));
 * }
 * }</pre>
 *
 * <p>Lenses are the backbone of {@link FormSchema}: form fields bind directly to
 * lens targets, eliminating stringly-typed model access.
 *
 * @param <S> model (state) type
 * @param <V> field value type
 */
public record Lens<S, V>(Function<S, V> getter, BiFunction<S, V, S> setter) {

    public static <S, V> Lens<S, V> of(Function<S, V> getter, BiFunction<S, V, S> setter) {
        return new Lens<>(getter, setter);
    }

    public V get(S state) {
        return getter.apply(state);
    }

    public S set(S state, V value) {
        return setter.apply(state, value);
    }

    /** Composes this lens with another lens targeting a sub-model of this one. */
    public <Sub> Lens<S, Sub> andThen(Lens<V, Sub> inner) {
        return new Lens<>(
                state -> inner.getter.apply(getter.apply(state)),
                (state, value) -> setter.apply(state, inner.setter.apply(getter.apply(state), value))
        );
    }
}
