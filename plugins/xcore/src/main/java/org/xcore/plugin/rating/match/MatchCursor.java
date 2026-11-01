package org.xcore.plugin.rating.match;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Keyset position in the {@code (ended_at desc, _id desc)} order of a player's matches. */
record MatchCursor(long endedAt, String id) {

    static MatchCursor after(MatchRecord match) {
        return new MatchCursor(match.endedAt().toEpochMilli(), match.id());
    }

    String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((endedAt + ":" + id).getBytes(StandardCharsets.UTF_8));
    }

    /** @return the decoded cursor, or {@code null} for a first-page request */
    static @Nullable MatchCursor decode(@Nullable String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = text.indexOf(':');
            String id = text.substring(separator + 1);
            if (separator < 1 || id.isBlank()) {
                throw new IllegalArgumentException();
            }
            return new MatchCursor(Long.parseLong(text.substring(0, separator)), id);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    /** Whether {@code match} sorts strictly after this position. */
    boolean precedes(MatchRecord match) {
        long ended = match.endedAt().toEpochMilli();
        return ended < endedAt || (ended == endedAt && match.id().compareTo(id) < 0);
    }
}
