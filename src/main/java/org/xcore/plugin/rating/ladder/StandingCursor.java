package org.xcore.plugin.rating.ladder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Keyset position in the {@code (rating desc, uuid asc)} leaderboard order. */
record StandingCursor(int rating, String uuid) {

    static StandingCursor after(LadderStanding standing) {
        return new StandingCursor(standing.rating(), standing.uuid());
    }

    String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((rating + ":" + uuid).getBytes(StandardCharsets.UTF_8));
    }

    /** @return the decoded cursor, or {@code null} for a first-page request */
    static StandingCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String text = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = text.indexOf(':');
            String uuid = text.substring(separator + 1);
            if (separator < 1 || uuid.isBlank()) {
                throw new IllegalArgumentException();
            }
            return new StandingCursor(Integer.parseInt(text.substring(0, separator)), uuid);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid cursor", e);
        }
    }

    /** Whether {@code standing} sorts strictly after this position. */
    boolean precedes(LadderStanding standing) {
        return standing.rating() < rating
                || (standing.rating() == rating && standing.uuid().compareTo(uuid) > 0);
    }
}
