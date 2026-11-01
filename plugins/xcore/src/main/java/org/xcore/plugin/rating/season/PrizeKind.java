package org.xcore.plugin.rating.season;

import java.util.Locale;

/** How a season prize reaches its winner. */
public enum PrizeKind {
    /** Delivered automatically: the winner unlocks the badge named by the prize's value. */
    BADGE(true),
    /** Free text, such as a Discord Nitro code. An administrator hands it over and marks it delivered. */
    CUSTOM(false);

    private final boolean automatic;

    PrizeKind(boolean automatic) {
        this.automatic = automatic;
    }

    /** Whether the plugin delivers prizes of this kind by itself when the season is archived. */
    public boolean automatic() {
        return automatic;
    }

    /** @throws SeasonException when {@code text} names no kind */
    public static PrizeKind parse(String text) {
        String normalized = text == null ? "" : text.strip().toUpperCase(Locale.ROOT);
        for (PrizeKind kind : values()) {
            if (kind.name().equals(normalized)) {
                return kind;
            }
        }
        throw new SeasonException("Unknown prize kind '" + text + "'; use badge or custom");
    }
}
