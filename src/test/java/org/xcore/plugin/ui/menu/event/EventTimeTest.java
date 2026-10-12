package org.xcore.plugin.ui.menu.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.ui.menu.event.EventTime.Shortcut;
import org.xcore.plugin.ui.menu.event.EventTime.Step;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class EventTimeTest {

    private static final ZoneId ZONE = ZoneOffset.ofHours(3);
    /** 16.10.2026 17:52 at the server. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T14:52:30Z"), ZONE);

    private static long at(String local) {
        return java.time.LocalDateTime.parse(local).atZone(ZONE).toInstant().toEpochMilli();
    }

    @Test
    @DisplayName("a time not set yet starts from the next quarter of an hour")
    void step_fromUnset() {
        assertThat(EventTime.step(0L, Step.HOUR_FORTH, CLOCK)).isEqualTo(at("2026-10-16T19:00:00"));
        assertThat(EventTime.step(0L, Step.DAY_FORTH, CLOCK)).isEqualTo(at("2026-10-17T18:00:00"));
        assertThat(EventTime.step(0L, Step.MINUTE_FORTH, CLOCK)).isEqualTo(at("2026-10-16T18:15:00"));
    }

    @Test
    @DisplayName("a day and an hour move the time by themselves, across midnight too")
    void step_dayAndHour() {
        long time = at("2026-10-16T23:30:00");

        assertThat(EventTime.step(time, Step.HOUR_FORTH, CLOCK)).isEqualTo(at("2026-10-17T00:30:00"));
        assertThat(EventTime.step(time, Step.HOUR_BACK, CLOCK)).isEqualTo(at("2026-10-16T22:30:00"));
        assertThat(EventTime.step(time, Step.DAY_FORTH, CLOCK)).isEqualTo(at("2026-10-17T23:30:00"));
        assertThat(EventTime.step(time, Step.DAY_BACK, CLOCK)).isEqualTo(at("2026-10-15T23:30:00"));
    }

    @Test
    @DisplayName("minutes go by quarters, and a time between quarters snaps to the nearest one that way")
    void step_minutesSnapToQuarters() {
        assertThat(EventTime.step(at("2026-10-16T18:00:00"), Step.MINUTE_FORTH, CLOCK)).isEqualTo(at("2026-10-16T18:15:00"));
        assertThat(EventTime.step(at("2026-10-16T18:00:00"), Step.MINUTE_BACK, CLOCK)).isEqualTo(at("2026-10-16T17:45:00"));
        assertThat(EventTime.step(at("2026-10-16T18:07:00"), Step.MINUTE_FORTH, CLOCK)).isEqualTo(at("2026-10-16T18:15:00"));
        assertThat(EventTime.step(at("2026-10-16T18:07:00"), Step.MINUTE_BACK, CLOCK)).isEqualTo(at("2026-10-16T18:00:00"));
        assertThat(EventTime.step(at("2026-10-16T18:45:00"), Step.MINUTE_FORTH, CLOCK)).isEqualTo(at("2026-10-16T19:00:00"));
    }

    @Test
    @DisplayName("the shortcuts of a start count from now, in the server's zone")
    void shortcut_forStart() {
        assertThat(EventTime.shortcut(Shortcut.NOW, 0L, CLOCK)).isEqualTo(at("2026-10-16T17:52:00"));
        assertThat(EventTime.shortcut(Shortcut.IN_HOUR, 0L, CLOCK)).isEqualTo(at("2026-10-16T18:52:00"));
        assertThat(EventTime.shortcut(Shortcut.TODAY_EVENING, 0L, CLOCK)).isEqualTo(at("2026-10-16T20:00:00"));
        assertThat(EventTime.shortcut(Shortcut.TOMORROW_NOON, 0L, CLOCK)).isEqualTo(at("2026-10-17T12:00:00"));
    }

    @Test
    @DisplayName("the shortcuts of an end count from the start, or from now without one")
    void shortcut_forEnd() {
        long start = at("2026-10-20T18:00:00");

        assertThat(EventTime.shortcut(Shortcut.PLUS_HOUR, start, CLOCK)).isEqualTo(at("2026-10-20T19:00:00"));
        assertThat(EventTime.shortcut(Shortcut.PLUS_THREE_HOURS, start, CLOCK)).isEqualTo(at("2026-10-20T21:00:00"));
        assertThat(EventTime.shortcut(Shortcut.PLUS_DAY, start, CLOCK)).isEqualTo(at("2026-10-21T18:00:00"));
        assertThat(EventTime.shortcut(Shortcut.PLUS_HOUR, 0L, CLOCK)).isEqualTo(at("2026-10-16T18:52:00"));
    }

    @Test
    @DisplayName("a step and a shortcut are found by the id their buttons send")
    void ids() {
        for (Step step : Step.values()) {
            assertThat(Step.of(step.id())).isEqualTo(step);
        }
        for (Shortcut shortcut : Shortcut.values()) {
            assertThat(Shortcut.of(shortcut.id())).isEqualTo(shortcut);
        }
        assertThat(Step.of("nonsense")).isNull();
        assertThat(Shortcut.of("nonsense")).isNull();
        assertThat(Shortcut.forStart()).doesNotContainAnyElementsOf(Shortcut.forEnd());
    }

    @Test
    @DisplayName("times are written in the server's zone, which is named as an offset")
    void writing() {
        long time = at("2026-10-16T18:05:00");

        assertThat(EventTime.full(time, ZONE)).isEqualTo("16.10.2026 18:05");
        assertThat(EventTime.brief(time, ZONE)).isEqualTo("16.10 18:05");
        assertThat(EventTime.date(time, ZONE)).isEqualTo("16.10.2026");
        assertThat(EventTime.day(time, ZONE, Locale.ENGLISH)).isEqualTo("Fri, 16 Oct");
        assertThat(EventTime.hour(time, ZONE)).isEqualTo(18);
        assertThat(EventTime.minute(time, ZONE)).isEqualTo(5);
        assertThat(EventTime.zone(CLOCK)).isEqualTo("UTC+03:00");
        assertThat(EventTime.zone(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))).isEqualTo("UTC");
    }
}
