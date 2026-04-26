package org.xcore.protocol.generated.routes;

import static java.util.Map.entry;

import java.util.Map;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages;

public final class ModerationRoutes {
    private ModerationRoutes() {
        throw new AssertionError("No org.xcore.protocol.generated.routes.ModerationRoutes instances");
    }

    public record MessageKey(String messageType, int messageVersion) {}

    public record RouteResponseDescriptor(
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String stream
    ) {}

    public record RouteDescriptor(
            String family,
            String methodName,
            String messageType,
            int messageVersion,
            Class<?> payloadType,
            String kind,
            String stream,
            String targetScope,
            int ttlMs,
            boolean replayable,
            boolean idempotentConsumerRecommended,
            String owner,
            RouteResponseDescriptor response
    ) {}

    public static final RouteDescriptor MODERATION_BAN_CREATED_V1 = new RouteDescriptor(
            "moderation",
            "moderationBanCreatedV1Route",
            "moderation.ban.created",
            1,
            ModerationMessages.ModerationBanCreatedV1.class,
            "event",
            "xcore:evt:moderation:ban",
            "broadcast",
            120000,
            true,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_MUTE_CREATED_V1 = new RouteDescriptor(
            "moderation",
            "moderationMuteCreatedV1Route",
            "moderation.mute.created",
            1,
            ModerationMessages.ModerationMuteCreatedV1.class,
            "event",
            "xcore:evt:moderation:mute",
            "broadcast",
            120000,
            true,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_VOTE_KICK_CREATED_V1 = new RouteDescriptor(
            "moderation",
            "moderationVoteKickCreatedV1Route",
            "moderation.vote-kick.created",
            1,
            ModerationMessages.ModerationVoteKickCreatedV1.class,
            "event",
            "xcore:evt:moderation:votekick",
            "broadcast",
            120000,
            true,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_KICK_BANNED_COMMAND_V1 = new RouteDescriptor(
            "moderation",
            "moderationKickBannedCommandV1Route",
            "moderation.kick-banned.command",
            1,
            ModerationMessages.ModerationKickBannedCommandV1.class,
            "command",
            "xcore:cmd:kick-banned:{server}",
            "server",
            120000,
            false,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_PARDON_COMMAND_V1 = new RouteDescriptor(
            "moderation",
            "moderationPardonCommandV1Route",
            "moderation.pardon.command",
            1,
            ModerationMessages.ModerationPardonCommandV1.class,
            "command",
            "xcore:cmd:pardon-player:{server}",
            "server",
            120000,
            false,
            true,
            "moderation",
            null
    );

    public static final RouteDescriptor MODERATION_AUDIT_APPENDED_V1 = new RouteDescriptor(
            "moderation",
            "moderationAuditAppendedV1Route",
            "moderation.audit.appended",
            1,
            ModerationMessages.ModerationAuditAppendedV1.class,
            "event",
            "xcore:evt:moderation:audit",
            "broadcast",
            120000,
            true,
            true,
            "moderation",
            null
    );

    public static final Map<MessageKey, RouteDescriptor> ROUTES_BY_MESSAGE = Map.ofEntries(
            entry(key("moderation.ban.created", 1), MODERATION_BAN_CREATED_V1),
            entry(key("moderation.mute.created", 1), MODERATION_MUTE_CREATED_V1),
            entry(key("moderation.vote-kick.created", 1), MODERATION_VOTE_KICK_CREATED_V1),
            entry(key("moderation.kick-banned.command", 1), MODERATION_KICK_BANNED_COMMAND_V1),
            entry(key("moderation.pardon.command", 1), MODERATION_PARDON_COMMAND_V1),
            entry(key("moderation.audit.appended", 1), MODERATION_AUDIT_APPENDED_V1)
    );

    private static MessageKey key(String messageType, int messageVersion) {
        return new MessageKey(messageType, messageVersion);
    }
}
