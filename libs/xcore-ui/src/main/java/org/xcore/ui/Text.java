package org.xcore.ui;

import java.util.Map;
import java.util.Objects;

/**
 * Lazy, allocation-friendly text descriptor used by all UI nodes.
 * <p>
 * A {@code Text} is either an already-resolved raw string or a localization key
 * with arguments. Resolution happens only at {@link VNodeCompiler} build time,
 * against the per-player {@link LocalizerResolver}, so the same AST renders in
 * every player's language without rebuilding the tree.
 *
 * @see LocalizerResolver
 */
public sealed interface Text permits Text.Raw, Text.Localized, Text.Joined {

    /** Resolves this descriptor into a concrete string. Never returns {@code null}. */
    String resolve(LocalizerResolver resolver);

    static Text empty() {
        return new Raw("");
    }

    static Text raw(String text) {
        return new Raw(text == null ? "" : text);
    }

    static Text t(String key) {
        return new Localized(key, Map.of());
    }

    static Text t(String key, Map<String, Object> args) {
        return new Localized(key, args);
    }

    static Text t(String key, String argName, Object argValue) {
        return new Localized(key, Map.of(argName, argValue));
    }

    /** Concatenates multiple descriptors lazily; resolution happens once, at render time. */
    static Text join(Text first, Text second) {
        return new Joined(first, second);
    }

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
            args = args == null ? Map.of() : Map.copyOf(args);
        }

        @Override
        public String resolve(LocalizerResolver resolver) {
            return resolver == null
                    ? key
                    : resolver.format(key, args);
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
