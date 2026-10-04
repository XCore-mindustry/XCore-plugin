package org.xcore.plugin.rating.prize;

import org.xcore.plugin.rating.season.PrizeKind;
import org.xcore.plugin.rating.season.SeasonPodiumEntry;
import org.xcore.plugin.rating.season.SeasonPrize;

import java.time.Instant;
import java.util.Objects;

/**
 * One prize owed to one player. The prize is copied into the grant, so editing a season's prizes
 * later never changes what was promised to a finished podium.
 *
 * @param id        {@code seasonId:place:playerUuid:prizeIndex}, so creating a grant twice is a no-op
 * @param grantedBy who last changed its status: {@code system} or a person's actor key
 * @param note      why it failed, or what a person said when delivering it; empty when nothing to say
 */
public record PrizeGrant(
        String id,
        String seasonId,
        int place,
        String playerUuid,
        int prizeIndex,
        PrizeKind kind,
        String value,
        String description,
        PrizeStatus status,
        String grantedBy,
        Instant updatedAt,
        String note
) {
    public static final String SYSTEM = "system";

    public PrizeGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
        description = description == null ? "" : description;
        grantedBy = grantedBy == null ? SYSTEM : grantedBy;
        note = note == null ? "" : note;
    }

    public static String id(String seasonId, int place, String playerUuid, int prizeIndex) {
        return seasonId + ":" + place + ":" + playerUuid + ":" + prizeIndex;
    }

    /** A new, pending grant of {@code prize} to the player at {@code entry}. */
    public static PrizeGrant pending(String seasonId, SeasonPodiumEntry entry, int prizeIndex, SeasonPrize prize,
                                     Instant now) {
        return new PrizeGrant(id(seasonId, entry.place(), entry.uuid(), prizeIndex), seasonId, entry.place(),
                entry.uuid(), prizeIndex, prize.kind(), prize.value(), prize.description(), PrizeStatus.PENDING,
                SYSTEM, now, "");
    }

    public PrizeGrant with(PrizeStatus status, String by, String note, Instant now) {
        return new PrizeGrant(id, seasonId, place, playerUuid, prizeIndex, kind, value, description, status, by, now,
                note);
    }

    public SeasonPrize prize() {
        return new SeasonPrize(place, place, kind, value, description);
    }
}
