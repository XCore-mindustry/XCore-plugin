package com.ospx.flubundle.compiler;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FunctionCatalog {

    private final Map<String, FunctionSpec> specs;
    private final boolean allowUnknownFunctions;

    public FunctionCatalog(Map<String, FunctionSpec> specs, boolean allowUnknownFunctions) {
        this.specs = Collections.unmodifiableMap(new LinkedHashMap<>(specs));
        this.allowUnknownFunctions = allowUnknownFunctions;
    }

    public boolean isAllowUnknownFunctions() {
        return allowUnknownFunctions;
    }

    public boolean contains(String functionName) {
        return specs.containsKey(functionName);
    }

    public Optional<FunctionSpec> get(String functionName) {
        return Optional.ofNullable(specs.get(functionName));
    }

    public Collection<String> functionNames() {
        return specs.keySet();
    }

    public Optional<String> findSimilar(String name) {
        return Levenshtein.findClosest(name, specs.keySet(), 3);
    }

    public static FunctionCatalog defaults() {
        return builder().addDefaults().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, FunctionSpec> specs = new LinkedHashMap<>();
        private boolean allowUnknownFunctions = false;

        public Builder register(FunctionSpec spec) {
            specs.put(spec.name(), Objects.requireNonNull(spec, "spec must not be null"));
            return this;
        }

        public Builder allowUnknownFunctions(boolean allow) {
            this.allowUnknownFunctions = allow;
            return this;
        }

        public Builder addDefaults() {
            register(FunctionSpec.builder("DURATION")
                    .positionals(1, 1)
                    .option("style", OptionSpec.stringEnum("compact", "timer", "digital", "full", "words"))
                    .option("unit", OptionSpec.stringEnum("seconds", "millis", "ms", "minutes", "m", "hours", "h", "days", "d"))
                    .option("colored", OptionSpec.booleanLiteral())
                    .option("maxUnits", OptionSpec.integerLiteral(0, 100))
                    .build());

            register(FunctionSpec.builder("COLOR")
                    .positionals(1, 1)
                    .option("color", OptionSpec.stringWithoutChars('[', ']'))
                    .build());

            register(FunctionSpec.builder("STRIP")
                    .positionals(1, 1)
                    .build());

            register(FunctionSpec.builder("DATETIME")
                    .positionals(1, 1)
                    .option("dateStyle", OptionSpec.stringEnum("full", "long", "medium", "short"))
                    .option("timeStyle", OptionSpec.stringEnum("full", "long", "medium", "short"))
                    .option("skeleton", OptionSpec.stringLiteral())
                    .build());

            register(FunctionSpec.builder("NUMBER")
                    .positionals(1, 1)
                    .option("useGrouping", OptionSpec.booleanLiteral())
                    .option("minimumIntegerDigits", OptionSpec.integerLiteral(0, 100))
                    .option("minimumFractionDigits", OptionSpec.integerLiteral(0, 100))
                    .option("maximumFractionDigits", OptionSpec.integerLiteral(0, 100))
                    .option("minimumSignificantDigits", OptionSpec.integerLiteral(0, 100))
                    .option("maximumSignificantDigits", OptionSpec.integerLiteral(0, 100))
                    .option("currency", OptionSpec.stringLiteral())
                    .option("currencyDisplay", OptionSpec.stringLiteral())
                    .option("style", OptionSpec.stringEnum("decimal", "currency", "percent", "unit"))
                    .build());

            register(FunctionSpec.builder("CASE")
                    .positionals(1, 1)
                    .option("type", OptionSpec.stringEnum("upper", "lower", "capital", "title"))
                    .build());

            register(FunctionSpec.builder("COUNT")
                    .positionals(1, 1)
                    .build());

            register(FunctionSpec.builder("BOOLEAN")
                    .positionals(1, 1)
                    .build());

            register(FunctionSpec.builder("ABS")
                    .positionals(1, 1)
                    .build());

            register(FunctionSpec.builder("SIGN")
                    .positionals(1, 1)
                    .build());

            register(FunctionSpec.builder("OFFSET")
                    .positionals(1, 1)
                    .option("amount", OptionSpec.any())
                    .build());

            register(FunctionSpec.builder("TEMPORAL")
                    .positionals(1, 1)
                    .option("part", OptionSpec.stringEnum("year", "month", "day", "hour", "minute", "second"))
                    .build());

            register(FunctionSpec.builder("XTEMPORAL")
                    .positionals(1, 1)
                    .option("part", OptionSpec.stringEnum("year", "month", "day", "hour", "minute", "second"))
                    .build());

            register(FunctionSpec.builder("LIST")
                    .positionals(1, Integer.MAX_VALUE)
                    .option("style", OptionSpec.stringEnum("standard", "or", "unit"))
                    .build());

            register(FunctionSpec.builder("NUMSORT")
                    .positionals(1, 1)
                    .option("direction", OptionSpec.stringEnum("asc", "desc"))
                    .build());

            register(FunctionSpec.builder("STRINGSORT")
                    .positionals(1, 1)
                    .option("direction", OptionSpec.stringEnum("asc", "desc"))
                    .build());

            return this;
        }

        public FunctionCatalog build() {
            return new FunctionCatalog(specs, allowUnknownFunctions);
        }
    }
}
