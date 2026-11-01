package org.xcore.plugin.rating.view;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.rating.prize.PrizeStatus;
import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.SeasonPrize;

import java.util.List;

import static com.ospx.flubundle.Bundle.args;

/** How prizes and their delivery are worded for players. */
public final class PrizeText {
    private static final int BRIEF_LENGTH = 18;

    private PrizeText() {
    }

    /** What a prize is: its description when it has one, else the badge's name or the custom text. */
    public static String label(PrizeKind kind, String value, String description, Localization local) {
        if (description != null && !description.isBlank()) {
            return description;
        }
        if (kind == PrizeKind.BADGE) {
            Badge badge = Badge.byId(value);
            if (badge != null) {
                return badge.tag() + " " + local.t(badge.nameKey());
            }
        }
        return value;
    }

    public static String label(SeasonPrize prize, Localization local) {
        return label(prize.kind(), prize.value(), prize.description(), local);
    }

    /** "Season Champion, Discord Nitro" for the prizes of one place. */
    public static String labels(List<SeasonPrize> prizes, Localization local) {
        return String.join(", ", prizes.stream().map(prize -> label(prize, local)).toList());
    }

    /** A short mention for a leaderboard row: the first prize, and how many more there are. */
    public static String brief(List<SeasonPrize> prizes, Localization local) {
        if (prizes.isEmpty()) {
            return "";
        }
        String first = label(prizes.getFirst(), local);
        if (first.length() > BRIEF_LENGTH) {
            first = first.substring(0, BRIEF_LENGTH - 1) + "…";
        }
        return prizes.size() == 1 ? first : first + " +" + (prizes.size() - 1);
    }

    public static String status(PrizeStatus status, Localization local) {
        return local.t("prize-status-" + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** "1" or "1-3" as a ready line: places and what they win. */
    public static String entry(SeasonPrize prize, Localization local) {
        return local.t("season-menu-prize-entry", args("places", prize.places(), "prize", label(prize, local)));
    }
}
