package com.ospx.flubundle.compiler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record FunctionSpec(
        String name,
        int minPositionals,
        int maxPositionals,
        Map<String, OptionSpec> options,
        boolean allowExtraOptions
) {
    public FunctionSpec {
        Objects.requireNonNull(name, "name must not be null");
        options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public Optional<OptionSpec> findOption(String optionName) {
        return Optional.ofNullable(options.get(optionName));
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private int minPositionals = 0;
        private int maxPositionals = Integer.MAX_VALUE;
        private final Map<String, OptionSpec> options = new LinkedHashMap<>();
        private boolean allowExtraOptions = false;

        public Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
        }

        public Builder positionals(int exact) {
            this.minPositionals = exact;
            this.maxPositionals = exact;
            return this;
        }

        public Builder positionals(int min, int max) {
            this.minPositionals = min;
            this.maxPositionals = max;
            return this;
        }

        public Builder option(String optionName, OptionSpec spec) {
            this.options.put(optionName, Objects.requireNonNull(spec, "spec must not be null"));
            return this;
        }

        public Builder allowExtraOptions(boolean allow) {
            this.allowExtraOptions = allow;
            return this;
        }

        public FunctionSpec build() {
            return new FunctionSpec(name, minPositionals, maxPositionals, options, allowExtraOptions);
        }
    }
}
