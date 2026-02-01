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
        if (period == null || period.isBlank()) {
            return null;
        }

        String normalized = period.toLowerCase().strip();

        if (Strings.canParsePositiveInt(normalized)) {
            long millis = defaultUnit.toMillis(Strings.parseInt(normalized));
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

        return found ? Instant.ofEpochMilli(total.toMillis()) : null;
    }
}