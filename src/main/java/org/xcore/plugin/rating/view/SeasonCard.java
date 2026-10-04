package org.xcore.plugin.rating.view;

import java.util.List;

/**
 * A {@link SeasonOverview} written out in one player's language.
 *
 * @param title       "MiniPvP — Season 3"
 * @param lines       the deadline, the number of participants and the viewer's own standing
 * @param podiumTitle heading of the previous season's winners, empty when there are none
 * @param podium      one line per winner of the previous season
 * @param prizeTitle  heading of the running season's prizes, empty when it has none
 * @param prizes      one line per prize the running season promises
 */
public record SeasonCard(String title, List<String> lines, String podiumTitle, List<String> podium,
                         String prizeTitle, List<String> prizes) {
    public SeasonCard {
        title = title == null ? "" : title;
        lines = lines == null ? List.of() : List.copyOf(lines);
        podiumTitle = podiumTitle == null ? "" : podiumTitle;
        podium = podium == null ? List.of() : List.copyOf(podium);
        prizeTitle = prizeTitle == null ? "" : prizeTitle;
        prizes = prizes == null ? List.of() : List.copyOf(prizes);
    }

    public SeasonCard(String title, List<String> lines, String podiumTitle, List<String> podium) {
        this(title, lines, podiumTitle, podium, "", List.of());
    }
}
