package org.xcore.ui.form;

import mindustry.ui.builder.MenuResult;

import java.util.function.Function;

/**
 * Pattern-matching decoder for values extracted from {@link MenuResult#values}.
 * Never throws on malformed input: a failed decode yields a fallback so a
 * malicious/buggy client cannot crash the reducer.
 */
public final class ValueCodec<T> {

    private final String name;
    private final Function<Object, T> decoder;
    private final T fallback;

    private ValueCodec(String name, Function<Object, T> decoder, T fallback) {
        this.name = name;
        this.decoder = decoder;
        this.fallback = fallback;
    }

    public static ValueCodec<String> string() {
        return new ValueCodec<>("string", v -> {
            if (v instanceof String s) return s;
            return String.valueOf(v);
        }, "");
    }

    public static ValueCodec<Integer> integer() {
        return new ValueCodec<>("integer", v -> {
            if (v instanceof Integer i) return i;
            if (v instanceof Number n) return n.intValue();
            if (v instanceof String s) {
                try {
                    return Math.round(Float.parseFloat(s.trim()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }, 0);
    }

    public static ValueCodec<Float> flt() {
        return new ValueCodec<>("float", v -> {
            if (v instanceof Float f) return f;
            if (v instanceof Number n) return n.floatValue();
            if (v instanceof String s) {
                try {
                    return Float.parseFloat(s.trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }, 0f);
    }

    public static ValueCodec<Boolean> bool() {
        return new ValueCodec<>("boolean", v -> {
            if (v instanceof Boolean b) return b;
            if (v instanceof String s) return Boolean.parseBoolean(s.trim());
            return null;
        }, Boolean.FALSE);
    }

    /** Decodes a raw value; returns the fallback when the value is absent or malformed. */
    public T decode(Object raw) {
        if (raw == null) return fallback;
        T result = decoder.apply(raw);
        return result == null ? fallback : result;
    }

    /** Decodes directly from a {@link MenuResult}. */
    public T decode(MenuResult result, String fieldId) {
        return decode(result == null ? null : result.values.get(fieldId));
    }

    public String name() {
        return name;
    }

    public T fallback() {
        return fallback;
    }
}
