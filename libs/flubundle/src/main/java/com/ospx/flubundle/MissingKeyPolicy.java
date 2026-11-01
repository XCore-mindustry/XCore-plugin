package com.ospx.flubundle;

import arc.util.Log;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides what {@link Bundle#format(Locale, String, Map)} returns when no locale in the fallback
 * chain defines the requested message.
 */
@FunctionalInterface
public interface MissingKeyPolicy {

    /**
     * @param key    the requested message id (for attributes, {@code id.attribute})
     * @param args   the arguments passed to {@code format}, never {@code null}
     * @param locale the requested locale after normalization
     * @return the text to use instead of the message
     */
    String onMissing(String key, Map<String, Object> args, Locale locale);

    /** Returns the key itself. This is the default. */
    static MissingKeyPolicy returnKey() {
        return (key, args, locale) -> key;
    }

    /** Returns the key in brackets, {@code ⟦key⟧}, so that missing keys stand out on screen. */
    static MissingKeyPolicy bracketed() {
        return (key, args, locale) -> "⟦" + key + "⟧";
    }

    /** Throws {@link IllegalStateException}; meant for tests. */
    static MissingKeyPolicy throwing() {
        return (key, args, locale) -> {
            throw new IllegalStateException("Missing bundle key '" + key + "' for locale " + locale);
        };
    }

    /** Logs each missing key once and returns the key. */
    static MissingKeyPolicy logOnce() {
        return logOnce(returnKey());
    }

    /**
     * Logs each missing key once, then delegates.
     *
     * <p>At most {@code 512} distinct keys are remembered; later misses are delegated silently.
     */
    static MissingKeyPolicy logOnce(MissingKeyPolicy delegate) {
        Set<String> reported = ConcurrentHashMap.newKeySet();
        return (key, args, locale) -> {
            if (reported.size() < 512 && reported.add(key)) {
                Log.warn("[FluBundle] Missing bundle key '@' (requested locale @)", key, locale);
            }
            return delegate.onMissing(key, args, locale);
        };
    }
}
