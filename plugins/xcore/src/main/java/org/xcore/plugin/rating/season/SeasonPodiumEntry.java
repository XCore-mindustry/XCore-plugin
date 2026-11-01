package org.xcore.plugin.rating.season;

/**
 * One of a finished season's best players, snapshotted at closing so the record survives
 * later renames, account merges and Discord unlinking.
 *
 * @param league          name of the {@link org.xcore.plugin.rating.RatingLeague} the player finished in
 * @param discordId       empty when no Discord account was linked
 * @param discordUsername empty when no Discord account was linked
 */
public record SeasonPodiumEntry(
        int place,
        String uuid,
        int pid,
        String nickname,
        int rating,
        String league,
        int matches,
        int wins,
        String discordId,
        String discordUsername
) {
    public boolean discordLinked() {
        return !discordId.isBlank();
    }
}
