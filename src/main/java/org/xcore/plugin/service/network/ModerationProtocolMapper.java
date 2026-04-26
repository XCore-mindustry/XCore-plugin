package org.xcore.plugin.service.network;

import org.xcore.plugin.model.BanData;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationBanCreatedV1;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ExpirationInfoV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

import java.time.Instant;

final class ModerationProtocolMapper {
    private ModerationProtocolMapper() {
    }

    static ModerationBanCreatedV1 toBanCreated(BanData ban, String server, Instant occurredAt) {
        return new ModerationBanCreatedV1(
                new PlayerRefV1(ban.uuid, null, ban.name, normalizeOptional(ban.ip)),
                new ActorRefV1(resolveActorName(ban), normalizeOptional(ban.adminDiscordId), resolveActorType(ban)),
                resolveReason(ban.reason),
                toExpirationInfo(ban),
                normalizeOptional(server),
                occurredAt.toString()
        );
    }

    private static ExpirationInfoV1 toExpirationInfo(BanData ban) {
        if (ban.expireDate == null) {
            return new ExpirationInfoV1(null, true);
        }
        return new ExpirationInfoV1(ban.expireDate.toString(), false);
    }

    private static String resolveActorName(BanData ban) {
        return normalizeOptional(ban.adminName) == null ? "Unknown" : ban.adminName;
    }

    private static String resolveActorType(BanData ban) {
        return normalizeOptional(ban.adminDiscordId) == null ? "unknown" : "discord";
    }

    private static String resolveReason(String reason) {
        return normalizeOptional(reason) == null ? "Not Specified" : reason;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
