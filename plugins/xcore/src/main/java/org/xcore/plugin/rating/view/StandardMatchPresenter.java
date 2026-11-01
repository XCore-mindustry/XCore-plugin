package org.xcore.plugin.rating.view;

import com.ospx.flubundle.mindustry.ContentNames;
import mindustry.game.Team;
import mindustry.gen.Iconc;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.rating.match.MatchParticipant;
import org.xcore.plugin.rating.match.MatchRecord;

import java.util.Locale;
import java.util.Map;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.ui.kit.Texts.t;

/**
 * Matches of a team mode by side and line-up, matches of a free-for-all by place. Reasons and
 * ends are worded by the {@code match-history-reason-*}, {@code match-history-skip-*} and
 * {@code match-history-finish-*} texts; a mode with codes of its own overrides the method.
 */
public class StandardMatchPresenter implements MatchPresenter {
    private final char icon;

    public StandardMatchPresenter() {
        this(Iconc.star);
    }

    public StandardMatchPresenter(char icon) {
        this.icon = icon;
    }

    @Override
    public char icon() {
        return icon;
    }

    @Override
    public String outcome(MatchRecord match, MatchParticipant player, Localization local) {
        if (!match.rated()) {
            return t(local, "match-history-outcome-unrated") + " · " + skip(match, local);
        }
        if (!player.counted()) {
            return t(local, "match-history-outcome-uncounted") + " · " + reasonText(player, local);
        }
        if (!match.teams()) {
            return place(match, player, local);
        }
        String result = t(local, player.win() ? "match-history-outcome-win" : "match-history-outcome-loss");
        return result + " · " + lineup(match, player, local);
    }

    /** "3 v 3" for two teams, "2nd of 4 teams" for more. */
    protected String lineup(MatchRecord match, MatchParticipant player, Localization local) {
        Map<Integer, Integer> sizes = match.teamSizes();
        if (sizes.size() == 2 && player.team() != null && sizes.containsKey(player.team())) {
            int own = sizes.get(player.team());
            int other = sizes.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(player.team()))
                    .mapToInt(Map.Entry::getValue).sum();
            return t(local, "match-history-lineup", args("own", own, "other", other));
        }
        return t(local, "match-history-team-place", args("place", player.placement(), "teams", sizes.size()));
    }

    /** "#2 of 9", the winner's in gold. */
    protected String place(MatchRecord match, MatchParticipant player, Localization local) {
        String place = t(local, "match-history-place", args("place", player.placement(), "players", match.players()));
        return player.placement() == 1 ? "[gold]" + place + "[]" : place;
    }

    @Override
    public String reason(MatchRecord match, MatchParticipant player, Localization local) {
        if (!match.rated()) {
            return t(local, "match-history-not-counted", args("reason", skip(match, local)));
        }
        if (!player.counted()) {
            return t(local, "match-history-not-counted", args("reason", reasonText(player, local)));
        }
        // A share is worth saying only where the mode counted part of the match.
        Double share = player.participation();
        if (share != null && share > 0.0 && share < 1.0) {
            return t(local, "match-history-counted-share", args("percent", (int) Math.round(share * 100)));
        }
        return t(local, "match-history-counted", args("reason", reasonText(player, local)));
    }

    /** The participant's reason code in words. */
    protected String reasonText(MatchParticipant player, Localization local) {
        return t(local, "match-history-reason-" + player.reason().replace('_', '-'));
    }

    /** Why nobody's rating changed. */
    protected String skip(MatchRecord match, Localization local) {
        String reason = match.skipReason() == null ? "unknown" : match.skipReason();
        return t(local, "match-history-skip-" + reason.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    @Override
    public String finish(MatchRecord match, Localization local) {
        if (match.finish() == null || match.finish().isBlank() || "NATURAL".equals(match.finish())) {
            return "";
        }
        return t(local, "match-history-finish-" + match.finish().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    @Override
    public String figures(MatchParticipant participant, Localization local) {
        return "";
    }

    @Override
    public String team(int team, Localization local) {
        Team resolved = Team.get(team);
        String name = local != null && local.getLocale() != null
                ? ContentNames.vanilla().name(resolved, local.getLocale())
                : resolved.name;
        return "[#" + teamColor(team) + "]" + name + "[]";
    }

    @Override
    public String teamColor(int team) {
        return Team.get(team).color.toString().substring(0, 6);
    }
}
