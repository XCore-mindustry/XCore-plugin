package org.xcore.ui;

import java.util.Map;

/**
 * Per-player localization strategy used to resolve {@link Text#t(String)} descriptors
 * at render time.
 *
 * <p>The library itself never depends on a concrete localization backend; host
 * plugins bind FluBundle (or anything else) via this interface.
 */
@FunctionalInterface
public interface LocalizerResolver {

    /** Resolver that returns the key itself; used by tests and fallbacks. */
    LocalizerResolver IDENTITY = (key, args) -> key;

    /** Formats a localization key with optional arguments into the player's locale. */
    String format(String key, Map<String, Object> args);

    /** Convenience for key-only formatting. */
    default String format(String key) {
        return format(key, Map.of());
    }
}
