package com.ospx.flubundle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compile-time checked factories for message arguments.
 *
 * <p>Unlike {@link Bundle#args(Object...)}, a missing value or a non-string name is a compile
 * error rather than a runtime exception. Values may be {@code null}.
 *
 * <pre>{@code
 * bundle.format(locale, "votekick-fail", Args.of("target", target.coloredName()));
 * Args.builder().put("used", used).put("max", max).putIf(verbose, "detail", detail).build();
 * }</pre>
 */
public final class Args {

    private Args() {
    }

    public static Map<String, Object> empty() {
        return Collections.emptyMap();
    }

    public static Map<String, Object> of(String k1, Object v1) {
        return build(k1, v1);
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2) {
        return build(k1, v1, k2, v2);
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2, String k3, Object v3) {
        return build(k1, v1, k2, v2, k3, v3);
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                                         String k4, Object v4) {
        return build(k1, v1, k2, v2, k3, v3, k4, v4);
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                                         String k4, Object v4, String k5, Object v5) {
        return build(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }

    public static Map<String, Object> of(String k1, Object v1, String k2, Object v2, String k3, Object v3,
                                         String k4, Object v4, String k5, Object v5, String k6, Object v6) {
        return build(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6);
    }

    /** For messages with more than six arguments, or arguments that depend on a condition. */
    public static Builder builder() {
        return new Builder();
    }

    private static Map<String, Object> build(Object... pairs) {
        var map = new LinkedHashMap<String, Object>(pairs.length);
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    public static final class Builder {
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder put(String name, Object value) {
            values.put(name, value);
            return this;
        }

        public Builder putIf(boolean condition, String name, Object value) {
            if (condition) {
                values.put(name, value);
            }
            return this;
        }

        public Builder putAll(Map<String, ?> other) {
            values.putAll(other);
            return this;
        }

        public Map<String, Object> build() {
            return Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }
}
