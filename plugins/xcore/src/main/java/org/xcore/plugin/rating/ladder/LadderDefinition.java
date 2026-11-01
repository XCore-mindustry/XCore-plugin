package org.xcore.plugin.rating.ladder;

import org.xcore.plugin.rating.RatingPolicy;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Static description of one competitive ladder (a game mode's rating track).
 *
 * @param id             stable storage key, e.g. {@code "minipvp"} or {@code "hexed"}
 * @param displayNameKey localization key of the ladder name shown to players
 * @param policy         rating rules shared with the mode's calculator
 */
public record LadderDefinition(String id, String displayNameKey, RatingPolicy policy) {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");

    public LadderDefinition {
        if (id == null || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Ladder ID must match [a-z0-9][a-z0-9_-]{0,31}");
        }
        if (displayNameKey == null || displayNameKey.isBlank()) {
            throw new IllegalArgumentException("displayNameKey must not be blank");
        }
        Objects.requireNonNull(policy, "policy");
    }
}
