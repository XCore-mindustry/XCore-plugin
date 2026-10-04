package org.xcore.plugin.rating.season;

import java.util.Objects;

/**
 * What the players on a range of places win when the season ends.
 *
 * @param placeFrom   first place the prize goes to
 * @param placeTo     last place the prize goes to, equal to {@code placeFrom} for a single place
 * @param value       what is handed out: a badge id for {@link PrizeKind#BADGE}, free text for {@link PrizeKind#CUSTOM}
 * @param description what players are told it is; blank falls back to the value
 */
public record SeasonPrize(int placeFrom, int placeTo, PrizeKind kind, String value, String description) {
    public SeasonPrize {
        if (placeFrom < 1) throw new IllegalArgumentException("placeFrom must be positive");
        if (placeTo < placeFrom) throw new IllegalArgumentException("placeTo must not be below placeFrom");
        Objects.requireNonNull(kind, "kind");
        if (value == null || value.isBlank()) throw new IllegalArgumentException("value must not be blank");
        value = value.strip();
        description = description == null ? "" : description.strip();
    }

    public boolean covers(int place) {
        return place >= placeFrom && place <= placeTo;
    }

    /** Whether every place of this prize lies within {@code from..to}. */
    public boolean within(int from, int to) {
        return placeFrom >= from && placeTo <= to;
    }

    /** The words players see for this prize. */
    public String label() {
        return description.isBlank() ? value : description;
    }

    /** {@code 1} or {@code 1-3}. */
    public String places() {
        return placeFrom == placeTo ? String.valueOf(placeFrom) : placeFrom + "-" + placeTo;
    }
}
