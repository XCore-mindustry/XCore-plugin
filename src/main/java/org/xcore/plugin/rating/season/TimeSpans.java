package org.xcore.plugin.rating.season;

import java.time.Duration;
import java.time.Period;
import java.time.temporal.TemporalAmount;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the short time spans used in season configuration and commands, such as {@code 3mo} or {@code 24h}. */
final class TimeSpans {
    private static final Pattern SPAN = Pattern.compile("(\\d{1,6})(mo|[smhdwy])");

    private TimeSpans() {
    }

    /**
     * An exact span: {@code s}, {@code m}, {@code h}, {@code d} or {@code w}.
     *
     * @throws IllegalArgumentException when the text is not a positive span in those units
     */
    static Duration duration(String text) {
        Matcher span = match(text);
        long amount = Long.parseLong(span.group(1));
        return switch (span.group(2)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            case "d" -> Duration.ofDays(amount);
            case "w" -> Duration.ofDays(7 * amount);
            default -> throw new IllegalArgumentException("'" + text + "' must be an exact span (s, m, h, d, w)");
        };
    }

    /**
     * A span that may also be counted in calendar months ({@code mo}) and years ({@code y}),
     * which have no fixed length and are resolved against a date.
     *
     * @throws IllegalArgumentException when the text is not a positive span
     */
    static TemporalAmount calendar(String text) {
        Matcher span = match(text);
        int amount = Integer.parseInt(span.group(1));
        return switch (span.group(2)) {
            case "mo" -> Period.ofMonths(amount);
            case "y" -> Period.ofYears(amount);
            default -> duration(text);
        };
    }

    private static Matcher match(String text) {
        Matcher span = SPAN.matcher(text == null ? "" : text.strip().toLowerCase());
        if (!span.matches() || Long.parseLong(span.group(1)) == 0) {
            throw new IllegalArgumentException(
                    "'" + text + "' is not a time span; expected a positive number and a unit (s, m, h, d, w, mo, y)");
        }
        return span;
    }
}
