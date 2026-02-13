package org.xcore.plugin.service;

import arc.util.Strings;
import arc.util.Nullable;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Singleton
public class TimeService {
    private static final Pattern PERIOD_PATTERN = Pattern.compile("(\\d+)([mhdwy])");

    public @Nullable Instant parsePeriod(String period, TimeUnit defaultUnit) {
        return parsePeriod(period, defaultUnit, false);
    }

    public @Nullable Instant parsePeriod(String period, TimeUnit defaultUnit, boolean allowNegative) {
        if (period == null || period.isBlank()) return null;

        String normalized = period.toLowerCase().strip();
        boolean negative = normalized.startsWith("-");
        if (negative) normalized = normalized.substring(1);

        if (!allowNegative && negative) return null;

        if (Strings.canParsePositiveInt(normalized)) {
            long millis = defaultUnit.toMillis(Strings.parseInt(normalized));
            if (negative) millis = -millis;
            return Instant.ofEpochMilli(millis);
        }

        var matcher = PERIOD_PATTERN.matcher(normalized);
        Duration total = Duration.ZERO;
        boolean found = false;

        while (matcher.find()) {
            found = true;
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            Duration duration = switch (unit) {
                case "m" -> Duration.ofMinutes(value);
                case "h" -> Duration.ofHours(value);
                case "d" -> Duration.ofDays(value);
                case "w" -> Duration.ofDays(7L * value);
                case "y" -> Duration.ofDays(365L * value);
                default -> Duration.ZERO;
            };
            total = total.plus(duration);
        }

        if (!found) return null;

        long millis = total.toMillis();
        if (negative) millis = -millis;
        return Instant.ofEpochMilli(millis);
    }
}