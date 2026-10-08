package com.ospx.flubundle.functions;

import com.ibm.icu.number.LocalizedNumberFormatter;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.text.ListFormatter;
import com.ibm.icu.util.MeasureUnit;
import com.ibm.icu.util.ULocale;
import fluent.bundle.resolver.Scope;
import fluent.function.FluentFunction;
import fluent.function.FluentFunctionException;
import fluent.function.FluentFunctionFactory;
import fluent.function.Options;
import fluent.types.FluentError;
import fluent.types.FluentNumber;
import fluent.types.FluentString;
import fluent.types.FluentValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code DURATION($seconds, style: "compact" | "full" | "timer", unit: ..., colored: "true", maxUnits: N)}.
 *
 * <p>Unit names and their plural forms come from ICU4J (CLDR), so every locale gets its own words:
 * {@code compact} uses narrow units joined by spaces ({@code 1h 5m}, {@code 1 ч 5 мин}), {@code full}
 * uses full unit names joined by the locale's list pattern ({@code 1 hour, 5 minutes},
 * {@code 1 Stunde und 5 Minuten}). {@code timer} prints {@code [d:]hh:mm:ss} and is not localized.
 */
public enum DurationFunction implements FluentFunctionFactory<FluentFunction.Transform> {
    DURATION;

    private static final MeasureUnit[] UNITS = {MeasureUnit.DAY, MeasureUnit.HOUR, MeasureUnit.MINUTE, MeasureUnit.SECOND};
    private static final long[] UNIT_SECONDS = {86400, 3600, 60, 1};

    private static final Map<String, Formatters> FORMATTERS = new ConcurrentHashMap<>();

    @Override
    public FluentFunction.Transform create(Locale locale, Options options) {
        String style = options.asString("style").orElse("compact").toLowerCase(Locale.ROOT);
        String unit = options.asString("unit").orElse("seconds").toLowerCase(Locale.ROOT);
        boolean colored = options.asBoolean("colored")
                .orElseGet(() -> options.asInt("colored").orElse(0) != 0);
        int maxUnits = options.asInt("maxUnits").orElseGet(() -> {
            try {
                return options.asString("maxUnits").map(Integer::parseInt).orElse(0);
            } catch (NumberFormatException e) {
                return 0;
            }
        });

        return (parameters, scope) -> {
            FluentFunction.ensureInput(parameters);
            return parameters.positionals().<FluentValue<?>>map(val -> {
                if (val instanceof FluentError) {
                    return val;
                }
                long totalSeconds = extractTotalSeconds(val, unit, scope);
                String formatted = formatDuration(totalSeconds, style, colored, maxUnits, scope.locale());
                return FluentString.of(formatted);
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

    private static String formatDuration(long totalSec, String style, boolean colored, int maxUnits, Locale locale) {
        long absSec = Math.abs(totalSec);
        String sign = totalSec < 0 ? "-" : "";

        if ("timer".equals(style) || "digital".equals(style)) {
            long days = absSec / 86400;
            long hours = (absSec % 86400) / 3600;
            long minutes = (absSec % 3600) / 60;
            long seconds = absSec % 60;
            if (days > 0) {
                return String.format(Locale.ROOT, "%s%d:%02d:%02d:%02d", sign, days, hours, minutes, seconds);
            } else if (hours > 0) {
                return String.format(Locale.ROOT, "%s%02d:%02d:%02d", sign, hours, minutes, seconds);
            } else {
                return String.format(Locale.ROOT, "%s%02d:%02d", sign, minutes, seconds);
            }
        }

        boolean full = "full".equals(style) || "words".equals(style);
        Formatters formatters = formatters(locale == null ? Locale.ENGLISH : locale, full);

        List<String> parts = new ArrayList<>();
        long rest = absSec;
        for (int i = 0; i < UNITS.length; i++) {
            long value = rest / UNIT_SECONDS[i];
            rest %= UNIT_SECONDS[i];
            boolean lastUnit = i == UNITS.length - 1;
            if (value > 0 || (lastUnit && parts.isEmpty())) {
                parts.add(formatters.part(i, value, colored));
            }
        }

        if (maxUnits > 0 && parts.size() > maxUnits) {
            parts = parts.subList(0, maxUnits);
        }

        String joined = full ? formatters.list.format(parts) : String.join(" ", parts);
        return sign + normalizeSpaces(joined);
    }

    private static Formatters formatters(Locale locale, boolean full) {
        return FORMATTERS.computeIfAbsent(locale.toLanguageTag() + (full ? "#full" : "#compact"),
                key -> new Formatters(ULocale.forLocale(locale), full));
    }

    /** Immutable, thread-safe ICU formatters for one locale and style. */
    private static final class Formatters {
        private final LocalizedNumberFormatter number;
        private final LocalizedNumberFormatter[] units = new LocalizedNumberFormatter[UNITS.length];
        private final ListFormatter list;

        Formatters(ULocale locale, boolean full) {
            number = NumberFormatter.withLocale(locale);
            NumberFormatter.UnitWidth width = full ? NumberFormatter.UnitWidth.FULL_NAME : NumberFormatter.UnitWidth.NARROW;
            for (int i = 0; i < UNITS.length; i++) {
                units[i] = number.unit(UNITS[i]).unitWidth(width);
            }
            list = ListFormatter.getInstance(locale, ListFormatter.Type.UNITS, ListFormatter.Width.WIDE);
        }

        String part(int unit, long value, boolean colored) {
            String text = normalizeSpaces(units[unit].format(value).toString());
            if (!colored) {
                return text;
            }
            String digits = normalizeSpaces(number.format(value).toString());
            int at = text.indexOf(digits);
            if (at < 0) {
                return "[white]" + text + "[lightgray]";
            }
            return text.substring(0, at) + "[white]" + digits + "[lightgray]" + text.substring(at + digits.length());
        }
    }

    /** Mindustry's font has no narrow no-break space, and CLDR uses no-break spaces in several locales. */
    private static String normalizeSpaces(String text) {
        return text.replace('\u202F', ' ').replace('\u00A0', ' ');
    }

    @Override
    public boolean canCache() {
        return true;
    }
}
