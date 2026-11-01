package org.xcore.plugin.rating.view;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.season.Season;
import org.xcore.plugin.ui.menu.PlayerProfileUiController;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

/** How a season is worded for players: its title, its dates and the time it has left. */
public final class SeasonText {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ZoneId zone;
    private final Clock clock;

    public SeasonText(ZoneId zone, Clock clock) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public String title(Season season, Localization local) {
        return title(season.number(), season.name(), local);
    }

    /** The season's own name when it has one, otherwise "Season N". */
    public String title(int number, String name, Localization local) {
        return name != null && !name.isBlank() ? name : local.t("season-title", args("number", number));
    }

    public String remaining(Season season, Localization local) {
        return remaining(season.endsAt(), local);
    }

    /** Time left until {@code endsAt}, rounded up to the minute so "1h" never reads "59m". */
    public String remaining(Instant endsAt, Localization local) {
        long seconds = Math.max(0, endsAt.getEpochSecond() - clock.instant().getEpochSecond());
        int minutes = (int) Math.max(1, (seconds + 59) / 60);
        return PlayerProfileUiController.formatDuration(minutes, local);
    }

    /** A calendar date in the time zone seasons are scheduled in. */
    public String date(Instant instant) {
        return DATE.format(instant.atZone(zone));
    }
}
