package com.ospx.flubundle.functions;

import arc.util.Strings;
import fluent.function.FluentFunction;
import fluent.function.FluentFunctionFactory;
import fluent.function.Options;
import fluent.types.FluentError;
import fluent.types.FluentString;
import fluent.types.FluentValue;

import java.util.Locale;

public enum StripFunction implements FluentFunctionFactory<FluentFunction.Transform> {
    STRIP;

    @Override
    public FluentFunction.Transform create(Locale locale, Options options) {
        return (parameters, scope) -> {
            FluentFunction.ensureInput(parameters);
            return parameters.positionals().map(val -> {
                if (val instanceof FluentError) {
                    return val;
                }
                String text = scope.registry().implicitFormat(val, scope);
                return (FluentValue<?>) FluentString.of(Strings.stripColors(text));
            }).toList();
        };
    }

    @Override
    public boolean canCache() {
        return true;
    }
}
