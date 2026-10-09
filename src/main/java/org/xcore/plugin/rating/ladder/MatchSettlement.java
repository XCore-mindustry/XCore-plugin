package org.xcore.plugin.rating.ladder;

import org.jspecify.annotations.Nullable;
import org.xcore.plugin.rating.match.MatchReport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A finished match as a mode hands it to the ladder: the rating changes it already
 * calculated, or the reason the match does not count.
 *
 * @param algorithmVersion version of the calculator that produced the mutations
 * @param resultHash       fingerprint of the match result; a retry must present the same one
 * @param skipReason       non-null for an unrated match, which is recorded but changes nothing
 * @param endedAt          when the match ended, which decides the season it counts towards;
 *                         {@code null} means the moment it is settled
 * @param report           what the match history keeps of the match, built only once the ladder
 *                         has claimed the match, so a duplicate never pays for it; {@code null} keeps nothing
 */
public record MatchSettlement(
        String matchId,
        String algorithmVersion,
        String resultHash,
        List<StandingMutation> mutations,
        @Nullable String skipReason,
        @Nullable Instant endedAt,
        @Nullable Supplier<MatchReport> report
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

    public MatchSettlement(String matchId, String algorithmVersion, String resultHash, List<StandingMutation> mutations,
                           @Nullable String skipReason, @Nullable Instant endedAt) {
        this(matchId, algorithmVersion, resultHash, mutations, skipReason, endedAt, null);
    }

    public static MatchSettlement rated(String matchId, String algorithmVersion, String resultHash,
                                        List<StandingMutation> mutations) {
        return new MatchSettlement(matchId, algorithmVersion, resultHash, mutations, null, null, null);
    }

    public static MatchSettlement unrated(String matchId, String algorithmVersion, String resultHash, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        return new MatchSettlement(matchId, algorithmVersion, resultHash, List.of(), reason, null, null);
    }

    public MatchSettlement withEndedAt(Instant endedAt) {
        return new MatchSettlement(matchId, algorithmVersion, resultHash, mutations, skipReason,
                Objects.requireNonNull(endedAt, "endedAt"), report);
    }

    /**
     * This settlement with the match as the history is to keep it. The report leaves the result
     * hash alone: it describes the match, it does not decide it.
     */
    public MatchSettlement withReport(MatchReport report) {
        Objects.requireNonNull(report, "report");
        return withReport(() -> report);
    }

    /**
     * {@link #withReport(MatchReport)} for a report that costs reads to build (names, ratings):
     * the ladder asks for it only after claiming the match, on the thread that settles it.
     */
    public MatchSettlement withReport(Supplier<MatchReport> report) {
        return new MatchSettlement(matchId, algorithmVersion, resultHash, mutations, skipReason, endedAt,
                Objects.requireNonNull(report, "report"));
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
