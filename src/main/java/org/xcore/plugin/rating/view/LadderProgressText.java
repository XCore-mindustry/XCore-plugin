package org.xcore.plugin.rating.view;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.rating.ladder.LadderStanding;
import org.xcore.plugin.rating.prize.PrizeGrant;
import org.xcore.plugin.ui.menu.PlayerProfileUiController;

import java.util.ArrayList;
import java.util.List;

import static com.ospx.flubundle.Bundle.args;

/** The lines a {@link LadderProgress} is written out as, shared by the profile and {@code /season}. */
final class LadderProgressText {
    private static final int PROGRESS_BAR_WIDTH = 14;

    private final SeasonText seasons;

    LadderProgressText(SeasonText seasons) {
        this.seasons = seasons;
    }

    /** "⚔ MiniPvP: ◆ Titanium (1642 ELO)" */
    String headline(String icon, LadderProgress progress, Localization local) {
        return local.t("ladder-profile-headline", args(
                "icon", icon,
                "ladder", local.t(progress.ladder().displayNameKey()),
                "standing", standing(progress, local)));
    }

    /** "◆ Titanium (1642 ELO)" */
    String standing(LadderProgress progress, Localization local) {
        LadderStanding standing = progress.standing();
        RatingLeague league = standing.league();
        return local.t("ladder-profile-standing", args(
                "leagueIcon", league.icon(),
                "league", local.t(league.localizationKey()),
                "rating", standing.rating()));
    }

    /** Match record, rank and peak; for a player who has not played this season, where they will start. */
    List<String> details(LadderProgress progress, Localization local) {
        LadderStanding standing = progress.standing();
        if (!standing.placed()) {
            return List.of(local.t("ladder-profile-unplaced"));
        }
        List<String> details = new ArrayList<>();
        int winRate = Math.round(standing.wins() * 100f / standing.matches());
        details.add(local.t("ladder-profile-matches", args("matches", standing.matches())));
        details.add(local.t("ladder-profile-wins", args("wins", standing.wins(), "rate", winRate)));
        if (progress.rank() != null) {
            details.add(local.t("ladder-profile-rank", args("rank", progress.rank())));
        }
        if (standing.peakRating() > standing.rating()) {
            details.add(local.t("ladder-profile-peak", args("rating", standing.peakRating())));
        }
        return details;
    }

    /** Progress bar towards the next league. */
    String leagueProgress(LadderProgress progress, Localization local) {
        LadderStanding standing = progress.standing();
        RatingLeague league = standing.league();
        if (!league.hasNext()) {
            return local.t("player-stats-max-league");
        }
        RatingLeague next = league.next();
        String bar = PlayerProfileUiController.renderRatingLeagueProgressBar(league, standing.rating(), PROGRESS_BAR_WIDTH);
        return bar + "  " + local.t("player-stats-league-elo-left", args(
                "elo", Math.max(0, next.minimumRating() - standing.rating()),
                "league", next.icon() + " " + local.t(next.localizationKey())));
    }

    /** "Season 3 · ends in 12d 4h"; empty for a ladder without seasons. */
    List<String> seasonLine(LadderProgress progress, Localization local) {
        if (progress.season() == null) {
            return List.of();
        }
        String title = seasons.title(progress.season(), local);
        return List.of(progress.season().active()
                ? local.t("ladder-profile-season", args(
                        "season", title, "remaining", seasons.remaining(progress.season(), local)))
                : local.t("ladder-profile-season-closing", args("season", title)));
    }

    /** "Season 2 · prize: Season Champion (delivered)" for each prize the player has won or been promised. */
    List<String> prizes(LadderProgress progress, Localization local) {
        List<String> lines = new ArrayList<>();
        for (PrizeGrant grant : progress.prizes()) {
            lines.add(local.t("prize-grant-line", args(
                    "season", seasons.title(grant.seasonNumber(), "", local),
                    "prize", PrizeText.label(grant.kind(), grant.value(), grant.description(), local),
                    "status", PrizeText.status(grant.status(), local))));
        }
        return lines;
    }

    /** "Season 2 — #4, Titanium, 1642" for each earlier season the player took part in. */
    List<String> history(LadderProgress progress, Localization local) {
        List<String> lines = new ArrayList<>();
        for (LadderStanding past : progress.history()) {
            RatingLeague league = past.league();
            lines.add(local.t("ladder-profile-history", args(
                    "season", seasons.title(past.season(), "", local),
                    "rank", past.finalRank() != null ? "#" + past.finalRank() : "—",
                    "league", league.icon() + " " + local.t(league.localizationKey()),
                    "rating", past.rating())));
        }
        return lines;
    }
}
