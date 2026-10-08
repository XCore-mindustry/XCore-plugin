package com.ospx.flubundle;

import arc.util.Log;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This interface is used to provide default values for keys that are not found in any of the
 * sources.
 */
public interface DefaultValueFactory {
    String getDefaultValue(String key, Map<String, Object> args, Locale locale);

    /**
     * Wraps a factory so that every missing key is logged once.
     *
     * <p>At most {@code 512} distinct keys are remembered; later misses are delegated silently.
     */
    static DefaultValueFactory logMissing(DefaultValueFactory delegate) {
        Set<String> reported = ConcurrentHashMap.newKeySet();
        return (key, args, locale) -> {
            if (reported.size() < 512 && reported.add(key)) {
                Log.warn("[FluBundle] Missing bundle key '@' (requested locale @)", key, locale);
            }
            return delegate.getDefaultValue(key, args, locale);
        };
    }
}
