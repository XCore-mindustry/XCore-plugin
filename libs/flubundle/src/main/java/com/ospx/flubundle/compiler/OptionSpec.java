package com.ospx.flubundle.compiler;

import fluent.syntax.ast.Literal;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public interface OptionSpec {

    void validate(String functionName, String optionName, Literal<?> literal, List<Diagnostic> diagnostics, Path file, int line, String messageId);

    static OptionSpec stringEnum(String... allowedValues) {
        Set<String> validSet = Arrays.stream(allowedValues).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> lowerSet = validSet.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());

        return (functionName, optionName, literal, diagnostics, file, line, messageId) -> {
            if (literal instanceof Literal.StringLiteral str) {
                String val = str.value();
                if (!lowerSet.contains(val.toLowerCase(Locale.ROOT))) {
                    String suggestion = Levenshtein.findClosest(val, validSet, 2)
                            .map(s -> " Did you mean '" + s + "'?")
                            .orElse("");
                    diagnostics.add(Diagnostic.error(file, line, messageId,
                            String.format("Invalid value '%s' for option '%s' in %s(). Allowed values: %s.%s",
                                    val, optionName, functionName, validSet, suggestion)));
                }
            } else {
                diagnostics.add(Diagnostic.error(file, line, messageId,
                        String.format("Option '%s' in %s() must be a string literal, but got: %s",
                                optionName, functionName, literal.value())));
            }
        };
    }

    static OptionSpec stringLiteral() {
        return (functionName, optionName, literal, diagnostics, file, line, messageId) -> {
            if (!(literal instanceof Literal.StringLiteral)) {
                diagnostics.add(Diagnostic.error(file, line, messageId,
                        String.format("Option '%s' in %s() must be a string literal, but got: %s",
                                optionName, functionName, literal.value())));
            }
        };
    }

    static OptionSpec stringWithoutChars(char... forbiddenChars) {
        return (functionName, optionName, literal, diagnostics, file, line, messageId) -> {
            if (literal instanceof Literal.StringLiteral str) {
                String val = str.value();
                for (char c : forbiddenChars) {
                    if (val.indexOf(c) >= 0) {
                        diagnostics.add(Diagnostic.error(file, line, messageId,
                                String.format("Option '%s' in %s() must not contain character '%c': '%s'",
                                        optionName, functionName, c, val)));
                        break;
                    }
                }
            } else {
                diagnostics.add(Diagnostic.error(file, line, messageId,
                        String.format("Option '%s' in %s() must be a string literal, but got: %s",
                                optionName, functionName, literal.value())));
            }
        };
    }

    static OptionSpec booleanLiteral() {
        return (functionName, optionName, literal, diagnostics, file, line, messageId) -> {
            if (literal instanceof Literal.StringLiteral str) {
                String val = str.value().toLowerCase(Locale.ROOT);
                if (!val.equals("true") && !val.equals("false")) {
                    diagnostics.add(Diagnostic.error(file, line, messageId,
                            String.format("Option '%s' in %s() must be boolean ('true' or 'false'), but got: '%s'",
                                    optionName, functionName, str.value())));
                }
            } else if (literal instanceof Literal.NumberLiteral.LongLiteral num) {
                long val = num.value();
                if (val != 0 && val != 1) {
                    diagnostics.add(Diagnostic.error(file, line, messageId,
                            String.format("Option '%s' in %s() must be boolean (0 or 1), but got: %d",
                                    optionName, functionName, val)));
                }
            } else {
                diagnostics.add(Diagnostic.error(file, line, messageId,
                        String.format("Option '%s' in %s() must be boolean, but got: %s",
                                optionName, functionName, literal.value())));
            }
        };
    }

    static OptionSpec integerLiteral(long min, long max) {
        return (functionName, optionName, literal, diagnostics, file, line, messageId) -> {
            long val;
            if (literal instanceof Literal.NumberLiteral.LongLiteral num) {
                val = num.value();
            } else if (literal instanceof Literal.StringLiteral str) {
                try {
                    val = Long.parseLong(str.value().trim());
                } catch (NumberFormatException e) {
                    diagnostics.add(Diagnostic.error(file, line, messageId,
                            String.format("Option '%s' in %s() must be an integer, but got: '%s'",
                                    optionName, functionName, str.value())));
                    return;
                }
            } else {
                diagnostics.add(Diagnostic.error(file, line, messageId,
                        String.format("Option '%s' in %s() must be an integer, but got: %s",
                                optionName, functionName, literal.value())));
                return;
            }

            if (val < min || val > max) {
                diagnostics.add(Diagnostic.error(file, line, messageId,
                        String.format("Option '%s' in %s() must be between %d and %d, but got: %d",
                                optionName, functionName, min, max, val)));
            }
        };
    }

    static OptionSpec any() {
        return (functionName, optionName, literal, diagnostics, file, line, messageId) -> {};
    }
}
