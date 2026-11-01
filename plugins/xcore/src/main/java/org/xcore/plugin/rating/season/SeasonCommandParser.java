package org.xcore.plugin.rating.season;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** Reads the dates and time spans administrators type into season commands. */
public final class SeasonCommandParser {

    private SeasonCommandParser() {
    }

    /**
     * A moment written as {@code yyyy-MM-dd} (midnight) or {@code yyyy-MM-ddTHH:mm}, in {@code zone}.
     *
     * @throws IllegalArgumentException when the text is neither
     */
    public static Instant dateTime(String text, ZoneId zone) {
        String value = text == null ? "" : text.strip();
        try {
            if (value.length() <= 10) {
                return LocalDate.parse(value).atStartOfDay(zone).toInstant();
            }
            return LocalDateTime.parse(value.replace(' ', 'T')).atZone(zone).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("'" + text + "' is not a date; expected yyyy-MM-dd or yyyy-MM-ddTHH:mm");
        }
    }

    /**
     * {@code from} moved later by a span such as {@code 3d} or {@code 1mo}; calendar spans
     * are counted in {@code zone}.
     *
     * @throws IllegalArgumentException when the text is not a positive time span
     */
    public static Instant extend(Instant from, String span, ZoneId zone) {
        return from.atZone(zone).plus(TimeSpans.calendar(span)).toInstant();
    }

    /**
     * Places written as {@code 1} or {@code 1-3}.
     *
     * @return {@code {from, to}}
     * @throws IllegalArgumentException when the text is neither
     */
    public static int[] places(String text) {
        String value = text == null ? "" : text.strip();
        try {
            int dash = value.indexOf('-');
            int from = Integer.parseInt(dash < 0 ? value : value.substring(0, dash).strip());
            int to = dash < 0 ? from : Integer.parseInt(value.substring(dash + 1).strip());
            if (from < 1 || to < from) {
                throw new NumberFormatException();
            }
            return new int[]{from, to};
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a place; expected 1 or 1-3");
        }
    }
}
