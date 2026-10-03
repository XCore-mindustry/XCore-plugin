package org.xcore.plugin.rating.ladder;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * A finished match as a mode hands it to the ladder: the rating changes it already
 * calculated, or the reason the match does not count.
 *
 * @param algorithmVersion version of the calculator that produced the mutations
 * @param resultHash       fingerprint of the match result; a retry must present the same one
 * @param skipReason       non-null for an unrated match, which is recorded but changes nothing
 */
public record MatchSettlement(
        String matchId,
        String algorithmVersion,
        String resultHash,
        List<StandingMutation> mutations,
        @Nullable String skipReason
) {
    public MatchSettlement {
        if (matchId == null || matchId.isBlank()) throw new IllegalArgumentException("matchId must not be blank");
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion must not be blank");
        }
        if (resultHash == null || resultHash.isBlank()) throw new IllegalArgumentException("resultHash must not be blank");
        mutations = List.copyOf(Objects.requireNonNull(mutations, "mutations"));
        if (skipReason == null && mutations.isEmpty()) {
            throw new IllegalArgumentException("A rated match must change at least one standing");
        }
        if (skipReason != null && !mutations.isEmpty()) {
            throw new IllegalArgumentException("An unrated match must not change standings");
        }
        if (mutations.stream().map(StandingMutation::uuid).distinct().count() != mutations.size()) {
            throw new IllegalArgumentException("Duplicate player UUID");
        }
    }

    public static MatchSettlement rated(String matchId, String algorithmVersion, String resultHash,
                                        List<StandingMutation> mutations) {
        return new MatchSettlement(matchId, algorithmVersion, resultHash, mutations, null);
    }

    public static MatchSettlement unrated(String matchId, String algorithmVersion, String resultHash, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        return new MatchSettlement(matchId, algorithmVersion, resultHash, List.of(), reason);
    }

    public boolean rated() {
        return skipReason == null;
    }

    /** SHA-256 fingerprint of a mode's canonical result string. */
    public static String hash(String canonicalResult) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonicalResult.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
