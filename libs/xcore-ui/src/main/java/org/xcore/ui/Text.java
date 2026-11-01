package org.xcore.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Lazy, allocation-friendly text descriptor used by all UI nodes.
 * <p>
 * A {@code Text} is either an already-resolved raw string, a localization key
 * with named arguments, or a localization key with positional parameters.
 * Resolution happens only at {@link VNodeCompiler} build time, against the
 * {@link LocalizerResolver}, so the same AST renders in every player's language
 * without rebuilding the tree.
 *
 * @see LocalizerResolver
 */
public sealed interface Text permits Text.Raw, Text.Localized, Text.Positional, Text.Joined {

    /** Resolves this descriptor into a concrete string. Never returns {@code null}. */
    String resolve(LocalizerResolver resolver);

    // -------------------------------------------------------------------------
    // Factory Methods
    // -------------------------------------------------------------------------

    static Text empty() {
        return new Raw("");
    }

    static Text raw(String text) {
        return new Raw(text == null ? "" : text);
    }

    /** Key-only localized text descriptor. */
    static Text t(String key) {
        return new Localized(key, Map.of());
    }

    /** Localized text descriptor with named arguments map. */
    static Text t(String key, Map<String, Object> args) {
        return new Localized(key, args);
    }

    /** Shorthand for localized text with a single key-value named pair. */
    static Text t(String key, String argName, Object argValue) {
        Map<String, Object> map = new LinkedHashMap<>(1);
        map.put(Objects.requireNonNull(argName, "argName"), argValue);
        return new Localized(key, Collections.unmodifiableMap(map));
    }

    /**
     * Localized text descriptor with positional arguments (e.g. for Arc {@code I18NBundle} {0}, {1}).
     * Dedicated method name avoids any Java overload ambiguity with {@link #t}.
     */
    static Text pos(String key, Object... args) {
        return new Positional(key, args);
    }

    /** Alias for {@link #pos(String, Object...)}. */
    static Text positional(String key, Object... args) {
        return new Positional(key, args);
    }

    /** Concatenates two descriptors lazily; resolution happens once, at render time. */
    static Text join(Text first, Text second) {
        return new Joined(first, second);
    }

    /** Concatenates multiple descriptors lazily. */
    static Text join(Text... parts) {
        if (parts == null || parts.length == 0) return empty();
        if (parts.length == 1) return parts[0];
        Text acc = parts[0];
        for (int i = 1; i < parts.length; i++) {
            acc = new Joined(acc, parts[i]);
        }
        return acc;
    }

    /**
     * Constructs an unmodifiable, insertion-ordered map from alternating key-value pairs.
     * Supports null values. Zero external dependencies.
     *
     * <p>Example: {@code Text.args("page", 1, "total", 10)}
     */
    static Map<String, Object> args(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Map.of();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "Arguments must be key-value pairs (even count), got: " + keyValues.length);
        }
        Map<String, Object> map = new LinkedHashMap<>(keyValues.length / 2);
        for (int i = 0; i < keyValues.length; i += 2) {
            Object k = keyValues[i];
            if (k == null) {
                throw new IllegalArgumentException("Argument key at index " + i + " must not be null");
            }
            if (!(k instanceof String keyStr)) {
                throw new IllegalArgumentException(
                        "Argument key at index " + i + " must be a String, got: " + k.getClass().getName());
            }
            map.put(keyStr, keyValues[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    // -------------------------------------------------------------------------
    // Descriptors
    // -------------------------------------------------------------------------

    record Raw(String value) implements Text {
        public Raw {
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String resolve(LocalizerResolver resolver) {
            return value;
        }
    }

    record Localized(String key, Map<String, Object> args) implements Text {
        public Localized {
            Objects.requireNonNull(key, "key");
            args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
        }

        @Override
        public String resolve(LocalizerResolver resolver) {
            return resolver == null
                    ? key
                    : resolver.format(key, args);
        }
    }

    record Positional(String key, Object[] args) implements Text {
        private static final Object[] EMPTY_ARRAY = new Object[0];

        public Positional {
            Objects.requireNonNull(key, "key");
            args = (args == null || args.length == 0) ? EMPTY_ARRAY : args.clone();
        }

        @Override
        public Object[] args() {
            return args.length == 0 ? EMPTY_ARRAY : args.clone();
        }

        @Override
        public String resolve(LocalizerResolver resolver) {
            return resolver == null
                    ? key
                    : resolver.formatPositional(key, args);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Positional that)) return false;
            return key.equals(that.key) && Arrays.equals(args, that.args);
        }

        @Override
        public int hashCode() {
            return 31 * key.hashCode() + Arrays.hashCode(args);
        }

        @Override
        public String toString() {
            return "Positional[key=" + key + ", args=" + Arrays.toString(args) + "]";
        }
    }

    /** Lazily concatenated descriptor; resolution happens once, at render time. */
    record Joined(Text first, Text second) implements Text {
        public Joined {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }

        @Override
        public String resolve(LocalizerResolver resolver) {
            return first.resolve(resolver) + second.resolve(resolver);
        }
    }
}
