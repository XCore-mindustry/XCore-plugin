package org.xcore.ui.form;

import java.util.Optional;

/**
 * Declarative value constraint evaluated by {@link FormSchema} before reducer
 * actions run. Validators return the first error token (usually a localization
 * key) when the value is rejected.
 */
@FunctionalInterface
public interface Validator<T> {

    /** Returns an error token when the value is invalid, {@link Optional#empty()} otherwise. */
    Optional<String> validate(T value);

    static <T> Validator<T> notNull(String errorKey) {
        return v -> v == null ? Optional.of(errorKey) : Optional.empty();
    }

    static Validator<String> notBlank(String errorKey) {
        return v -> (v == null || v.isBlank()) ? Optional.of(errorKey) : Optional.empty();
    }

    static Validator<String> lengthBetween(int min, int max, String errorKey) {
        return v -> {
            if (v == null) return Optional.of(errorKey);
            int len = v.codePointCount(0, v.length());
            return (len < min || len > max) ? Optional.of(errorKey) : Optional.empty();
        };
    }

    static Validator<String> matches(String regex, String errorKey) {
        return v -> (v == null || !v.matches(regex)) ? Optional.of(errorKey) : Optional.empty();
    }

    static Validator<Float> range(float min, float max, String errorKey) {
        return v -> (v == null || v < min || v > max) ? Optional.of(errorKey) : Optional.empty();
    }

    static Validator<Integer> intRange(int min, int max, String errorKey) {
        return v -> (v == null || v < min || v > max) ? Optional.of(errorKey) : Optional.empty();
    }
}
