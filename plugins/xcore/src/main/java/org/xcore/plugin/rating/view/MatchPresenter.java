package org.xcore.plugin.rating.view;

import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.match.MatchParticipant;
import org.xcore.plugin.rating.match.MatchRecord;

/**
 * How a ladder's matches read in the match history: a mode words its own outcomes, reasons and
 * figures here, so the history needs to know nothing of the mode. A mode registers one with
 * {@link MatchPresenters}; a ladder without one is shown by {@link StandardMatchPresenter}.
 *
 * <p>Every method gets a {@code null} localization in tests and returns the bundle key then.</p>
 */
public interface MatchPresenter {

    /** Glyph of the ladder's tab. */
    char icon();

    /** What the match came to for {@code player}, in a few words: "Victory · 3 v 3", "#2 of 9 · 5 hexes". */
    String outcome(MatchRecord match, MatchParticipant player, Localization local);

    /** Why the match did or did not change {@code player}'s rating, as one line. */
    String reason(MatchRecord match, MatchParticipant player, Localization local);

    /** How the match ended when that is worth saying ("by timer"); empty for an ordinary end. */
    String finish(MatchRecord match, Localization local);

    /** The mode's own figures of a participant ("7 hexes"); empty for none. */
    String figures(MatchParticipant participant, Localization local);

    /** The name of a team in its colour. */
    String team(int team, Localization local);

    /** The colour of a team, as {@code rrggbb}. */
    String teamColor(int team);
}
