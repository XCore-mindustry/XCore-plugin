package com.ospx.flubundle.functions;

import fluent.bundle.resolver.Scope;
import fluent.function.FluentFunction;
import fluent.function.FluentFunctionException;
import fluent.function.FluentFunctionFactory;
import fluent.function.Options;
import fluent.types.FluentError;
import fluent.types.FluentNumber;
import fluent.types.FluentString;
import fluent.types.FluentValue;

import java.util.Locale;

public enum DurationFunction implements FluentFunctionFactory<FluentFunction.Transform> {
    DURATION;

    @Override
    public FluentFunction.Transform create(Locale locale, Options options) {
        String style = options.asString("style").orElse("compact").toLowerCase(Locale.ROOT);
        String unit = options.asString("unit").orElse("seconds").toLowerCase(Locale.ROOT);

        return (parameters, scope) -> {
            FluentFunction.ensureInput(parameters);
            return parameters.positionals().map(val -> {
                if (val instanceof FluentError) {
                    return val;
                }
                long totalSeconds = extractTotalSeconds(val, unit, scope);
                String formatted = formatDuration(totalSeconds, style, scope.locale());
                return (FluentValue<?>) FluentString.of(formatted);
            }).toList();
        };
    }

    private static long extractTotalSeconds(FluentValue<?> val, String unit, Scope scope) {
        double raw;
        if (val instanceof FluentNumber<?> num) {
            raw = num.value().doubleValue();
        } else {
            String str = scope.registry().implicitFormat(val, scope);
            try {
                raw = Double.parseDouble(str.trim());
            } catch (NumberFormatException e) {
                throw FluentFunctionException.of("Invalid numeric input in DURATION(): '%s'", str);
            }
        }

        if (!Double.isFinite(raw)) {
            throw FluentFunctionException.of("Non-finite number in DURATION(): %s", raw);
        }

        return switch (unit) {
            case "millis", "ms" -> (long) Math.floor(raw / 1000.0);
            case "minutes", "m" -> (long) Math.floor(raw * 60.0);
            case "hours", "h" -> (long) Math.floor(raw * 3600.0);
            case "days", "d" -> (long) Math.floor(raw * 86400.0);
            default -> (long) Math.floor(raw);
        };
    }

    private static String formatDuration(long totalSec, String style, Locale locale) {
        long absSec = Math.abs(totalSec);
        long days = absSec / 86400;
        long hours = (absSec % 86400) / 3600;
        long minutes = (absSec % 3600) / 60;
        long seconds = absSec % 60;

        if ("timer".equals(style) || "digital".equals(style)) {
            String sign = totalSec < 0 ? "-" : "";
            if (days > 0) {
                return String.format(Locale.ROOT, "%s%d:%02d:%02d:%02d", sign, days, hours, minutes, seconds);
            } else if (hours > 0) {
                return String.format(Locale.ROOT, "%s%02d:%02d:%02d", sign, hours, minutes, seconds);
            } else {
                return String.format(Locale.ROOT, "%s%02d:%02d", sign, minutes, seconds);
            }
        }

        String lang = locale.getLanguage().toLowerCase(Locale.ROOT);
        String dUnit = "d", hUnit = "h", mUnit = "m", sUnit = "s";
        if ("ru".equals(lang)) {
            dUnit = "д"; hUnit = "ч"; mUnit = "м"; sUnit = "с";
        } else if ("uk".equals(lang) || "be".equals(lang)) {
            dUnit = "д"; hUnit = "г"; mUnit = "хв"; sUnit = "с";
        }

        StringBuilder sb = new StringBuilder();
        if (totalSec < 0) {
            sb.append("-");
        }
        if (days > 0) sb.append(days).append(dUnit).append(" ");
        if (hours > 0) sb.append(hours).append(hUnit).append(" ");
        if (minutes > 0) sb.append(minutes).append(mUnit).append(" ");
        if (seconds > 0 || sb.isEmpty() || (sb.length() == 1 && totalSec < 0)) {
            sb.append(seconds).append(sUnit);
        }
        return sb.toString().trim();
    }

    @Override
    public boolean canCache() {
        return true;
    }
}
