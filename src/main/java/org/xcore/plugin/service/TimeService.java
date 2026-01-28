package org.xcore.plugin.service;

import arc.util.Strings;
import arc.util.Nullable;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class TimeService {
    private static final Pattern periodPattern = Pattern.compile("([0-9]+)([hdwmy])");

    public @Nullable Instant parsePeriod(String period, TimeUnit defaultUnit) {
        if (period == null) return null;
        period = period.toLowerCase();
        Matcher matcher = periodPattern.matcher(period);
        Instant instant = Instant.EPOCH;

        boolean found = false;
        while (matcher.find()) {
            found = true;
            int num = Strings.parseInt(matcher.group(1));
            String typ = matcher.group(2);
            switch (typ) {
                case "m" -> instant = instant.plusMillis(TimeUnit.MINUTES.toMillis(num));
                case "h" -> instant = instant.plusMillis(TimeUnit.HOURS.toMillis(num));
                case "d" -> instant = instant.plusMillis(TimeUnit.DAYS.toMillis(num));
                case "w" -> instant = instant.plusMillis(TimeUnit.DAYS.toMillis(7L * num));
                case "y" -> instant = instant.plusMillis(TimeUnit.DAYS.toMillis(365L * num));
            }
        }

        if (!found && Strings.canParsePositiveInt(period)) {
            return Instant.now().plusMillis(defaultUnit.toMillis(Strings.parseInt(period)));
        }

        if (found) {
            return instant;
        }

        return null;
    }
}