package org.xcore.plugin.ui.menu.event;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * The times of an event: how the time screen moves one, and how they are written.
 *
 * <p>A time is epoch millis, 0 for none. Everything is counted in the zone of the server's
 * clock: the server cannot know the zone a player is in, so the menu says whose time it shows.
 */
public final class EventTime {

    /** Minutes go by quarters of an hour: an event is planned for a round time. */
    public static final int MINUTE_STEP = 15;

    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private EventTime() {
    }

    /** A press that moves the time by one unit. */
    public enum Step {
        DAY_BACK, DAY_FORTH, HOUR_BACK, HOUR_FORTH, MINUTE_BACK, MINUTE_FORTH;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Step of(String id) {
            for (Step step : values()) {
                if (step.id().equals(id)) return step;
            }
            return null;
        }
    }

    /** A press that sets the time at once. */
    public enum Shortcut {
        NOW("event-ui-time-now"),
        IN_HOUR("event-ui-time-in-hour"),
        TODAY_EVENING("event-ui-time-today-evening"),
        TOMORROW_NOON("event-ui-time-tomorrow-noon"),
        /** Counted from the start of the event, for its end. */
        PLUS_HOUR("event-ui-time-plus-hour"),
        PLUS_THREE_HOURS("event-ui-time-plus-three-hours"),
        PLUS_DAY("event-ui-time-plus-day");

        private final String key;

        Shortcut(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Shortcut of(String id) {
            for (Shortcut shortcut : values()) {
                if (shortcut.id().equals(id)) return shortcut;
            }
            return null;
        }

        /** The shortcuts for the start of an event. */
        public static List<Shortcut> forStart() {
            return List.of(NOW, IN_HOUR, TODAY_EVENING, TOMORROW_NOON);
        }

        /** The shortcuts for the end of an event: how long it lasts. */
        public static List<Shortcut> forEnd() {
            return List.of(PLUS_HOUR, PLUS_THREE_HOURS, PLUS_DAY);
        }
    }

    /**
     * {@code time} after {@code step}. A time not set yet starts from the next quarter of an
     * hour, so the first press already shows a time worth keeping.
     */
    public static long step(long time, Step step, Clock clock) {
        ZonedDateTime from = time > 0 ? at(time, clock.getZone()) : nextQuarter(clock);
        ZonedDateTime moved = switch (step) {
            case DAY_BACK -> from.minusDays(1);
            case DAY_FORTH -> from.plusDays(1);
            case HOUR_BACK -> from.minusHours(1);
            case HOUR_FORTH -> from.plusHours(1);
            case MINUTE_BACK -> {
                int over = from.getMinute() % MINUTE_STEP;
                yield from.minusMinutes(over == 0 ? MINUTE_STEP : over);
            }
            case MINUTE_FORTH -> from.plusMinutes(MINUTE_STEP - from.getMinute() % MINUTE_STEP);
        };
        return moved.truncatedTo(ChronoUnit.MINUTES).toInstant().toEpochMilli();
    }

    /**
     * The time {@code shortcut} stands for.
     *
     * @param anchor what the shortcuts of {@link Shortcut#forEnd()} count from; now when it is not set
     */
    public static long shortcut(Shortcut shortcut, long anchor, Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        ZonedDateTime from = anchor > 0 ? at(anchor, clock.getZone()) : now;
        ZonedDateTime picked = switch (shortcut) {
            case NOW -> now;
            case IN_HOUR -> now.plusHours(1);
            case TODAY_EVENING -> {
                ZonedDateTime evening = now.with(LocalTime.of(20, 0));
                yield evening.isAfter(now) ? evening : evening.plusDays(1);
            }
            case TOMORROW_NOON -> now.plusDays(1).with(LocalTime.NOON);
            case PLUS_HOUR -> from.plusHours(1);
            case PLUS_THREE_HOURS -> from.plusHours(3);
            case PLUS_DAY -> from.plusDays(1);
        };
        return picked.toInstant().toEpochMilli();
    }

    private static ZonedDateTime nextQuarter(Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        int over = now.getMinute() % MINUTE_STEP;
        return over == 0 ? now : now.plusMinutes(MINUTE_STEP - over);
    }

    private static ZonedDateTime at(long time, ZoneId zone) {
        return Instant.ofEpochMilli(time).atZone(zone);
    }

    // ------------------------------------------------------------------ writing

    /** "16.10.2026 18:00" */
    public static String full(long time, ZoneId zone) {
        return FULL.format(at(time, zone));
    }

    /** "16.10 18:00" */
    public static String brief(long time, ZoneId zone) {
        return SHORT.format(at(time, zone));
    }

    /** "16.10.2026" */
    public static String date(long time, ZoneId zone) {
        return DATE.format(at(time, zone));
    }

    /** "Fri, 16 Oct" in the language of the player. */
    public static String day(long time, ZoneId zone, Locale locale) {
        return DateTimeFormatter.ofPattern("EEE, d MMM", locale == null ? Locale.ENGLISH : locale).format(at(time, zone));
    }

    public static int hour(long time, ZoneId zone) {
        return at(time, zone).getHour();
    }

    public static int minute(long time, ZoneId zone) {
        return at(time, zone).getMinute();
    }

    /** "UTC+03:00": the zone the times are in, as an offset a player can compare with their own. */
    public static String zone(Clock clock) {
        ZoneOffset offset = clock.getZone().getRules().getOffset(clock.instant());
        return offset.getTotalSeconds() == 0 ? "UTC" : "UTC" + offset.getId();
    }
}
