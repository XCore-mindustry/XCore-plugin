package org.xcore.ui.form;

import org.xcore.ui.Lens;
import mindustry.ui.builder.MenuResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Declarative mapping between form field IDs and model lenses.
 *
 * <p>On submit, {@link #apply(Object, MenuResult)} decodes every bound field,
 * runs its validators in declaration order and produces an atomic
 * {@link FormResult}: either a fully-updated model, or the original model plus
 * per-field error tokens.
 */
public final class FormSchema<Model> {

    private record Binding<Model, T>(
            String fieldId,
            Lens<Model, T> lens,
            ValueCodec<T> codec,
            List<Validator<T>> validators
    ) {
    }

    private final List<Binding<Model, ?>> bindings = new ArrayList<>();

    public <T> FormSchema<Model> bind(String fieldId, Lens<Model, T> lens, ValueCodec<T> codec, List<Validator<T>> validators) {
        Objects.requireNonNull(fieldId, "fieldId");
        Objects.requireNonNull(lens, "lens");
        Objects.requireNonNull(codec, "codec");
        for (Binding<Model, ?> existing : bindings) {
            if (existing.fieldId.equals(fieldId)) {
                throw new IllegalArgumentException("Duplicate form field binding: " + fieldId);
            }
        }
        bindings.add(new Binding<>(fieldId, lens, codec, validators == null ? List.of() : List.copyOf(validators)));
        return this;
    }

    public List<String> fieldIds() {
        return bindings.stream().map(Binding::fieldId).toList();
    }

    /** Decodes, validates and applies all bound fields. Atomic: on error, the model is returned untouched. */
    public FormResult<Model> apply(Model model, MenuResult result) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(result, "result");

        Map<String, String> errors = new LinkedHashMap<>();
        Model current = model;

        for (Binding<Model, ?> binding : bindings) {
            current = applyOne(binding, current, result, errors);
        }

        return new FormResult<>(current, errors);
    }

    private <T> Model applyOne(Binding<Model, T> binding, Model model, MenuResult result, Map<String, String> errors) {
        T decoded = binding.codec.decode(result, binding.fieldId);
        for (Validator<T> validator : binding.validators) {
            Optional<String> error = validator.validate(decoded);
            if (error.isPresent()) {
                errors.put(binding.fieldId, error.get());
                return model;
            }
        }
        return binding.lens.set(model, decoded);
    }

    /** Outcome of {@link FormSchema#apply(Object, MenuResult)}. */
    public record FormResult<Model>(Model model, Map<String, String> errors) {

        public FormResult {
            errors = errors == null ? Map.of() : Map.copyOf(errors);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public boolean hasError(String fieldId) {
            return errors.containsKey(fieldId);
        }

        public String error(String fieldId) {
            return errors.get(fieldId);
        }

        public String firstError() {
            return errors.values().stream().findFirst().orElse(null);
        }
    }
}
