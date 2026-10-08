package com.ospx.flubundle.functions;

import com.ospx.flubundle.mindustry.Markup;
import fluent.function.FluentFunction;
import fluent.function.FluentFunctionFactory;
import fluent.function.Options;
import fluent.types.FluentError;
import fluent.types.FluentString;
import fluent.types.FluentValue;

import java.util.Locale;

/**
 * {@code ESCAPE($text)}: renders the argument literally, so color tags inside it are shown as text
 * instead of changing the message's colors. See {@link Markup#escape(String)}.
 */
public enum EscapeFunction implements FluentFunctionFactory<FluentFunction.Transform> {
    ESCAPE;

    @Override
    public FluentFunction.Transform create(Locale locale, Options options) {
        return (parameters, scope) -> {
            FluentFunction.ensureInput(parameters);
            return parameters.positionals().map(val -> {
                if (val instanceof FluentError) {
                    return val;
                }
                String text = scope.registry().implicitFormat(val, scope);
                return (FluentValue<?>) FluentString.of(Markup.escape(text));
            }).toList();
        };
    }

    @Override
    public boolean canCache() {
        return true;
    }
}
