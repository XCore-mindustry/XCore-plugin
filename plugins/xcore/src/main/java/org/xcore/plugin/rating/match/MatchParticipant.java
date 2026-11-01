package org.xcore.plugin.rating.match;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One player's part in a recorded match, as it was when the match ended.
 *
 * @param name          the name the player had in the match; it is shown as it was, even after a rename
 * @param team          the team played for, {@code null} in a mode without teams
 * @param placement     1 for the winners
 * @param ratingBefore  rating the mode calculated from
 * @param delta         rating change the mode calculated; 0 when the match did not count
 * @param ratingAfter   rating the player was left with
 * @param counted       whether the match changed this player's rating
 * @param reason        why it did or did not count, a code the mode words for players
 * @param participation share of the match that counted, {@code null} where the mode has no such thing
 * @param extra         a few of the mode's own figures, such as hexes held
 */
public record MatchParticipant(
        String uuid,
        String name,
        @Nullable Integer team,
        int placement,
        boolean win,
        int ratingBefore,
        int delta,
        int ratingAfter,
        boolean counted,
        String reason,
        @Nullable Double participation,
        Map<String, Integer> extra
) {
    private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    public MatchParticipant {
        if (uuid == null || uuid.isBlank()) throw new IllegalArgumentException("uuid must not be blank");
        name = name == null ? "" : name;
        if (placement < 1) throw new IllegalArgumentException("placement must be positive");
        if (reason == null || !CODE.matcher(reason).matches()) {
            throw new IllegalArgumentException("reason must match [a-z][a-z0-9_]{0,31}");
        }
        extra = extra == null ? Map.of() : Map.copyOf(extra);
        for (String key : extra.keySet()) {
            if (!CODE.matcher(key).matches()) throw new IllegalArgumentException("Invalid figure name: " + key);
        }
    }

    /** A participant whose rating the match changed by {@code delta}. */
    public static MatchParticipant counted(String uuid, String name, @Nullable Integer team, int placement,
                                           boolean win, int ratingBefore, int delta, String reason) {
        return new MatchParticipant(uuid, name, team, placement, win, ratingBefore, delta, ratingBefore + delta,
                true, reason, null, Map.of());
    }

    /** A participant the match left at {@code rating}. */
    public static MatchParticipant uncounted(String uuid, String name, @Nullable Integer team, int placement,
                                             boolean win, int rating, String reason) {
        return new MatchParticipant(uuid, name, team, placement, win, rating, 0, rating, false, reason, null, Map.of());
    }

    public MatchParticipant withRatingAfter(int rating) {
        return new MatchParticipant(uuid, name, team, placement, win, ratingBefore, delta, rating, counted, reason,
                participation, extra);
    }

    public MatchParticipant withParticipation(@Nullable Double share) {
        return new MatchParticipant(uuid, name, team, placement, win, ratingBefore, delta, ratingAfter, counted,
                reason, share, extra);
    }

    public MatchParticipant withExtra(Map<String, Integer> figures) {
        return new MatchParticipant(uuid, name, team, placement, win, ratingBefore, delta, ratingAfter, counted,
                reason, participation, Objects.requireNonNull(figures, "figures"));
    }

    /** One of the mode's figures, 0 when it was not recorded. */
    public int figure(String key) {
        return extra.getOrDefault(key, 0);
    }
}
