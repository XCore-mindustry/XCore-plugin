package org.xcore.ui;

import arc.Core;
import arc.util.I18NBundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Strategy used to resolve localized text descriptors (such as {@link Text#t(String)}
 * and {@link Text#pos(String, Object...)}) at render time.
 *
 * <p>The framework itself is completely decoupled from any specific localization engine.
 * Consumers can plug in Arc's {@link I18NBundle}, FluBundle, custom bundle systems,
 * or arbitrary formatting functions via this interface or its factory methods.
 */
@FunctionalInterface
public interface LocalizerResolver {

    /** Default resolver that echoes the key itself; used by tests and fallbacks. */
    LocalizerResolver IDENTITY = new LocalizerResolver() {
        @Override
        public String format(String key, Map<String, Object> args) {
            return key;
        }

        @Override
        public String formatPositional(String key, Object... args) {
            return key;
        }

        @Override
        public String format(String key) {
            return key;
        }

        @Override
        public boolean has(String key) {
            return false;
        }
    };

    // -------------------------------------------------------------------------
    // Core Resolution Contract
    // -------------------------------------------------------------------------

    /**
     * Formats a localization key with named arguments into the target locale.
     *
     * @param key  the translation key
     * @param args named arguments map (never null, may be empty)
     * @return the localized string, or the key/fallback representation if missing
     */
    String format(String key, Map<String, Object> args);

    /**
     * Convenience formatting for a key without arguments.
     */
    default String format(String key) {
        return format(key, Map.of());
    }

    /**
     * Formats a localization key with positional arguments.
     *
     * <p>The default implementation bridges positional parameters to named parameters
     * using both numeric index keys ({@code "0"}, {@code "1"}, ...) and identifier-safe
     * keys ({@code "arg0"}, {@code "arg1"}, ...).
     *
     * @param key  the translation key
     * @param args positional parameters
     * @return the formatted string
     */
    default String formatPositional(String key, Object... args) {
        if (args == null || args.length == 0) {
            return format(key, Map.of());
        }
        Map<String, Object> map = new LinkedHashMap<>(args.length * 2);
        for (int i = 0; i < args.length; i++) {
            String idx = Integer.toString(i);
            map.put(idx, args[i]);
            map.put("arg" + idx, args[i]);
        }
        return format(key, Collections.unmodifiableMap(map));
    }

    /**
     * Convenience varargs overload delegating directly to {@link #formatPositional(String, Object...)}.
     */
    default String format(String key, Object... args) {
        return formatPositional(key, args);
    }

    /**
     * Checks whether this resolver contains the given translation key.
     * Used by composite resolvers to avoid unnecessary lookups.
     */
    default boolean has(String key) {
        return true;
    }

    /**
     * Combines this resolver with a fallback resolver.
     */
    default LocalizerResolver orElse(LocalizerResolver fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return composite(this, fallback);
    }

    // -------------------------------------------------------------------------
    // Static Factories
    // -------------------------------------------------------------------------

    /**
     * Creates an adapter from an Arc {@link I18NBundle}.
     */
    static LocalizerResolver from(I18NBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        return new I18NBundleResolver(bundle);
    }

    /**
     * Creates a dynamic resolver that delegates to {@link arc.Core#bundle}.
     * If {@code Core.bundle} is uninitialized (e.g. in test suites), safely returns the key.
     */
    static LocalizerResolver core() {
        return new LocalizerResolver() {
            @Override
            public String format(String key, Map<String, Object> args) {
                I18NBundle b = Core.bundle;
                return b == null ? key : LocalizerResolver.from(b).format(key, args);
            }

            @Override
            public String formatPositional(String key, Object... args) {
                I18NBundle b = Core.bundle;
                return b == null ? key : LocalizerResolver.from(b).formatPositional(key, args);
            }

            @Override
            public boolean has(String key) {
                I18NBundle b = Core.bundle;
                return b != null && b.has(key);
            }
        };
    }

    /**
     * Creates an adapter from a named formatting function (e.g. FluBundle method reference).
     */
    static LocalizerResolver from(BiFunction<String, Map<String, Object>, String> function) {
        Objects.requireNonNull(function, "function");
        return (key, args) -> {
            String res = function.apply(key, args == null ? Map.of() : args);
            return res != null ? res : key;
        };
    }

    /**
     * Creates an adapter from a simple key-to-string lookup function (e.g. {@code map::get}).
     */
    static LocalizerResolver from(Function<String, String> lookup) {
        Objects.requireNonNull(lookup, "lookup");
        return new LocalizerResolver() {
            @Override
            public String format(String key, Map<String, Object> args) {
                String res = lookup.apply(key);
                return res != null ? res : key;
            }

            @Override
            public String formatPositional(String key, Object... args) {
                String res = lookup.apply(key);
                return res != null ? res : key;
            }

            @Override
            public String format(String key) {
                String res = lookup.apply(key);
                return res != null ? res : key;
            }

            @Override
            public boolean has(String key) {
                return lookup.apply(key) != null;
            }
        };
    }

    /**
     * Creates an adapter from a positional formatting function (e.g. {@code java.text.MessageFormat}).
     */
    static LocalizerResolver positional(BiFunction<String, Object[], String> function) {
        Objects.requireNonNull(function, "function");
        return new PositionalFunctionResolver(function);
    }

    /**
     * Creates a composite resolver that queries resolvers in priority order until a valid translation is found.
     */
    static LocalizerResolver composite(LocalizerResolver... resolvers) {
        Objects.requireNonNull(resolvers, "resolvers");
        List<LocalizerResolver> list = new ArrayList<>(resolvers.length);
        for (LocalizerResolver r : resolvers) {
            if (r != null && r != IDENTITY) {
                list.add(r);
            }
        }
        if (list.isEmpty()) return IDENTITY;
        if (list.size() == 1) return list.get(0);
        return new CompositeResolver(List.copyOf(list));
    }

    // -------------------------------------------------------------------------
    // Global Default Resolver Management
    // -------------------------------------------------------------------------

    final class DefaultHolder {
        private static final AtomicReference<LocalizerResolver> CURRENT =
                new AtomicReference<>(LocalizerResolver.IDENTITY);
    }

    /**
     * Gets the current global default resolver used when none is explicitly provided.
     */
    static LocalizerResolver getDefault() {
        return DefaultHolder.CURRENT.get();
    }

    /**
     * Sets the global default resolver.
     */
    static void setDefault(LocalizerResolver resolver) {
        DefaultHolder.CURRENT.set(resolver == null ? LocalizerResolver.IDENTITY : resolver);
    }

    // -------------------------------------------------------------------------
    // Implementation Classes
    // -------------------------------------------------------------------------

    final class I18NBundleResolver implements LocalizerResolver {
        private final I18NBundle bundle;

        I18NBundleResolver(I18NBundle bundle) {
            this.bundle = bundle;
        }

        @Override
        public boolean has(String key) {
            return bundle.has(key);
        }

        @Override
        public String formatPositional(String key, Object... args) {
            if (!bundle.has(key)) {
                return key;
            }
            if (args == null || args.length == 0) {
                return bundle.get(key);
            }
            return bundle.format(key, args);
        }

        @Override
        public String format(String key, Map<String, Object> args) {
            if (!bundle.has(key)) {
                return key;
            }
            if (args == null || args.isEmpty()) {
                return bundle.get(key);
            }
            // Check if map contains sequential numeric keys "0", "1", ...
            if (args.containsKey("0")) {
                int count = 0;
                while (args.containsKey(Integer.toString(count))) {
                    count++;
                }
                Object[] arr = new Object[count];
                for (int i = 0; i < count; i++) {
                    arr[i] = args.get(Integer.toString(i));
                }
                return bundle.format(key, arr);
            }

            // Named template placeholder replacement: "{param}" -> value
            String pattern = bundle.get(key);
            for (Map.Entry<String, Object> entry : args.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (pattern.contains(placeholder)) {
                    pattern = pattern.replace(placeholder, String.valueOf(entry.getValue()));
                }
            }
            return pattern;
        }
    }

    final class PositionalFunctionResolver implements LocalizerResolver {
        private final BiFunction<String, Object[], String> function;

        PositionalFunctionResolver(BiFunction<String, Object[], String> function) {
            this.function = function;
        }

        @Override
        public String formatPositional(String key, Object... args) {
            String res = function.apply(key, args == null ? new Object[0] : args);
            return res != null ? res : key;
        }

        @Override
        public String format(String key, Map<String, Object> args) {
            if (args == null || args.isEmpty()) {
                return formatPositional(key);
            }
            if (args.containsKey("0")) {
                int count = 0;
                while (args.containsKey(Integer.toString(count))) {
                    count++;
                }
                Object[] arr = new Object[count];
                for (int i = 0; i < count; i++) {
                    arr[i] = args.get(Integer.toString(i));
                }
                return formatPositional(key, arr);
            }
            return formatPositional(key, args.values().toArray());
        }
    }

    final class CompositeResolver implements LocalizerResolver {
        private final List<LocalizerResolver> resolvers;

        CompositeResolver(List<LocalizerResolver> resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public boolean has(String key) {
            for (LocalizerResolver r : resolvers) {
                if (r.has(key)) return true;
            }
            return false;
        }

        @Override
        public String format(String key, Map<String, Object> args) {
            for (LocalizerResolver r : resolvers) {
                if (r.has(key)) {
                    String res = r.format(key, args);
                    if (isValidTranslation(key, res)) {
                        return res;
                    }
                }
            }
            return key;
        }

        @Override
        public String formatPositional(String key, Object... args) {
            for (LocalizerResolver r : resolvers) {
                if (r.has(key)) {
                    String res = r.formatPositional(key, args);
                    if (isValidTranslation(key, res)) {
                        return res;
                    }
                }
            }
            return key;
        }

        private boolean isValidTranslation(String key, String result) {
            return result != null && !result.equals("???" + key + "???");
        }
    }
}
