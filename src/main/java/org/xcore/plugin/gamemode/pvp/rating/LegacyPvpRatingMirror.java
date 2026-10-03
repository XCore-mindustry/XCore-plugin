package org.xcore.plugin.gamemode.pvp.rating;

import arc.util.Log;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.ladder.LadderStanding;

import java.util.Collection;

/**
 * Temporary bridge: copies MiniPvP standings into the {@code pvp_*} fields of
 * {@link PlayerData}. The ladder is the source of truth; the copy only feeds the profile
 * menu and the Discord bot until both read standings themselves.
 */
final class LegacyPvpRatingMirror {
    private final PlayerDataRepository players;

    LegacyPvpRatingMirror(PlayerDataRepository players) {
        this.players = players;
    }

    /** Blocking. A failed copy is only logged: the settlement itself already succeeded. */
    void persist(Collection<LadderStanding> standings) {
        if (players == null) return;
        for (LadderStanding standing : standings) {
            try {
                players.mirrorPvpStanding(standing.uuid(), standing.rating(), standing.matches(), standing.wins());
            } catch (RuntimeException e) {
                Log.warn("Failed to mirror MiniPvP rating for @: @", standing.uuid(), e.getMessage());
            }
        }
    }

    /** Keeps a live session in step, so a later whole-document save does not write stale values. */
    static void apply(PlayerData data, LadderStanding standing) {
        data.pvpRating = standing.rating();
        data.pvpMatches = standing.matches();
        data.pvpWins = standing.wins();
    }
}
