package com.ospx.flubundle.functions;

import fluent.function.FluentFunction;
import fluent.function.FluentFunctionException;
import fluent.function.FluentFunctionFactory;
import fluent.function.Options;
import fluent.types.FluentError;
import fluent.types.FluentString;
import fluent.types.FluentValue;

import java.util.Locale;

public enum ColorFunction implements FluentFunctionFactory<FluentFunction.Transform> {
    COLOR;

    @Override
    public FluentFunction.Transform create(Locale locale, Options options) {
        String color = options.asString("color").orElse("accent");
        if (color.isBlank() || color.contains("[") || color.contains("]")) {
            throw FluentFunctionException.of("Invalid color option in COLOR(): '%s'", color);
        }

        return (parameters, scope) -> {
            FluentFunction.ensureInput(parameters);
            return parameters.positionals().map(val -> {
                if (val instanceof FluentError) {
                    return val;
                }
                String text = scope.registry().implicitFormat(val, scope);
                return (FluentValue<?>) FluentString.of("[" + color + "]" + text + "[]");
            }).toList();
        };
    }

    @Override
    public boolean canCache() {
        return true;
    }
}
