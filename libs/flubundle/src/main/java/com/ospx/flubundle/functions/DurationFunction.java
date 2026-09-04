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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public enum DurationFunction implements FluentFunctionFactory<FluentFunction.Transform> {
    DURATION;

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

    record UnitPart(long value, String unitName) {}

    private static String formatDuration(long totalSec, String style, boolean colored, int maxUnits, Locale locale) {
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
        boolean isFull = "full".equals(style) || "words".equals(style);

        List<UnitPart> parts = new ArrayList<>();
        if (days > 0) parts.add(new UnitPart(days, getUnitLabel(days, "day", isFull, lang)));
        if (hours > 0) parts.add(new UnitPart(hours, getUnitLabel(hours, "hour", isFull, lang)));
        if (minutes > 0) parts.add(new UnitPart(minutes, getUnitLabel(minutes, "minute", isFull, lang)));
        if (seconds > 0 || parts.isEmpty()) {
            parts.add(new UnitPart(seconds, getUnitLabel(seconds, "second", isFull, lang)));
        }

        if (maxUnits > 0 && parts.size() > maxUnits) {
            parts = parts.subList(0, maxUnits);
        }

        StringBuilder sb = new StringBuilder();
        if (totalSec < 0) {
            sb.append("-");
        }

        for (int i = 0; i < parts.size(); i++) {
            UnitPart p = parts.get(i);
            if (i > 0) sb.append(" ");
            if (colored) {
                sb.append("[white]").append(p.value()).append("[lightgray]");
                if (isFull) sb.append(" ");
                sb.append(p.unitName());
            } else {
                sb.append(p.value());
                if (isFull) sb.append(" ");
                sb.append(p.unitName());
            }
        }
        return sb.toString();
    }

    private static String getUnitLabel(long count, String type, boolean isFull, String lang) {
        if (!isFull) {
            return switch (type) {
                case "day" -> ("ru".equals(lang) || "uk".equals(lang) || "be".equals(lang)) ? "д" : "d";
                case "hour" -> ("ru".equals(lang)) ? "ч" : (("uk".equals(lang) || "be".equals(lang)) ? "г" : "h");
                case "minute" -> ("ru".equals(lang)) ? "м" : (("uk".equals(lang) || "be".equals(lang)) ? "хв" : "m");
                case "second" -> ("ru".equals(lang) || "uk".equals(lang) || "be".equals(lang)) ? "с" : "s";
                default -> "";
            };
        }

        if ("ru".equals(lang)) {
            return switch (type) {
                case "day" -> slavicPlural(count, "день", "дня", "дней");
                case "hour" -> slavicPlural(count, "час", "часа", "часов");
                case "minute" -> slavicPlural(count, "минута", "минуты", "минут");
                case "second" -> slavicPlural(count, "секунда", "секунды", "секунд");
                default -> "";
            };
        } else if ("uk".equals(lang)) {
            return switch (type) {
                case "day" -> slavicPlural(count, "день", "дні", "днів");
                case "hour" -> slavicPlural(count, "година", "години", "годин");
                case "minute" -> slavicPlural(count, "хвилина", "хвилини", "хвилин");
                case "second" -> slavicPlural(count, "секунда", "секунди", "секунд");
                default -> "";
            };
        } else if ("be".equals(lang)) {
            return switch (type) {
                case "day" -> slavicPlural(count, "дзень", "дні", "дзён");
                case "hour" -> slavicPlural(count, "гадзіна", "гадзіны", "гадзін");
                case "minute" -> slavicPlural(count, "хвіліна", "хвіліны", "хвілін");
                case "second" -> slavicPlural(count, "секунда", "секунды", "секунд");
                default -> "";
            };
        } else {
            return switch (type) {
                case "day" -> count == 1 ? "day" : "days";
                case "hour" -> count == 1 ? "hour" : "hours";
                case "minute" -> count == 1 ? "minute" : "minutes";
                case "second" -> count == 1 ? "second" : "seconds";
                default -> "";
            };
        }
    }

    private static String slavicPlural(long count, String one, String few, String many) {
        long n = Math.abs(count);
        long mod10 = n % 10;
        long mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) {
            return one;
        } else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) {
            return few;
        } else {
            return many;
        }
    }

    @Override
    public boolean canCache() {
        return true;
    }
}
