package org.xcore.protocol.generated.messages.moderation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.ExpirationInfoV1;
import org.xcore.protocol.generated.shared.ModerationTargetRefV1;
import org.xcore.protocol.generated.shared.PlayerCommandTargetV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;
import org.xcore.protocol.generated.shared.VoteKickParticipantV1;

public final class ModerationMessages {
    private ModerationMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.moderation.ModerationMessages instances");
    }

    public record ModerationAuditAppendedV1(
            ModerationAuditAppendedV1EntryType entryType,
            ModerationTargetRefV1 target,
            ActorRefV1 actor,
            String reason,
            String server,
            String occurredAt,
            Map<String, Object> details
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "moderation.audit.appended";
        public static final int MESSAGE_VERSION = 1;

        public ModerationAuditAppendedV1 {
            Objects.requireNonNull(entryType, "entryType must not be null");
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (reason.length() < 1) {
                throw new IllegalArgumentException("reason must be at least 1 characters");
            }
            if (server != null) {
                Objects.requireNonNull(server, "server must not be null");
                if (server.length() < 1) {
                    throw new IllegalArgumentException("server must be at least 1 characters");
                }
            }
            if (occurredAt != null) {
                Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            }
            if (details != null) {
                details = Objects.requireNonNull(details, "details must not be null");
                for (Map.Entry<String, Object> entry : details.entrySet()) {
                    Objects.requireNonNull(entry.getKey(), "details keys must not be null");
                    if (entry.getValue() == null) {
                        continue;
                    }
                    if (!(entry.getValue() instanceof String) && !(entry.getValue() instanceof Number) && !(entry.getValue() instanceof Boolean)) {
                        throw new IllegalArgumentException("details values must be one of: string, number, boolean, null");
                    }
                }
                details = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(details));
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            if (entryType != null) {
                payload.put("entryType", entryType.toString());
            }
            payload.put("target", target.toPayload());
            payload.put("actor", actor.toPayload());
            payload.put("reason", reason);
            if (server != null) {
                payload.put("server", server);
            }
            if (occurredAt != null) {
                payload.put("occurredAt", occurredAt);
            }
            if (details != null) {
                payload.put("details", java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(details)));
            }
            return payload;
        }
    }

    public record ModerationBanCreatedV1(
            PlayerRefV1 target,
            ActorRefV1 actor,
            String reason,
            ExpirationInfoV1 expiration,
            String server,
            String occurredAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "moderation.ban.created";
        public static final int MESSAGE_VERSION = 1;

        public ModerationBanCreatedV1 {
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (reason.length() < 1) {
                throw new IllegalArgumentException("reason must be at least 1 characters");
            }
            if (expiration != null) {
                Objects.requireNonNull(expiration, "expiration must not be null");
            }
            if (server != null) {
                Objects.requireNonNull(server, "server must not be null");
                if (server.length() < 1) {
                    throw new IllegalArgumentException("server must be at least 1 characters");
                }
            }
            if (occurredAt != null) {
                Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("target", target.toPayload());
            payload.put("actor", actor.toPayload());
            payload.put("reason", reason);
            if (expiration != null) {
                payload.put("expiration", expiration.toPayload());
            }
            if (server != null) {
                payload.put("server", server);
            }
            if (occurredAt != null) {
                payload.put("occurredAt", occurredAt);
            }
            return payload;
        }
    }

    public record ModerationKickBannedCommandV1(
            PlayerCommandTargetV1 target,
            String server,
            String requestedAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "moderation.kick-banned.command";
        public static final int MESSAGE_VERSION = 1;

        public ModerationKickBannedCommandV1 {
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            if (requestedAt != null) {
                Objects.requireNonNull(requestedAt, "requestedAt must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("target", target.toPayload());
            payload.put("server", server);
            if (requestedAt != null) {
                payload.put("requestedAt", requestedAt);
            }
            return payload;
        }
    }

    public record ModerationMuteCreatedV1(
            PlayerRefV1 target,
            ActorRefV1 actor,
            String reason,
            ExpirationInfoV1 expiration,
            String server,
            String occurredAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "moderation.mute.created";
        public static final int MESSAGE_VERSION = 1;

        public ModerationMuteCreatedV1 {
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (reason.length() < 1) {
                throw new IllegalArgumentException("reason must be at least 1 characters");
            }
            if (expiration != null) {
                Objects.requireNonNull(expiration, "expiration must not be null");
            }
            if (server != null) {
                Objects.requireNonNull(server, "server must not be null");
                if (server.length() < 1) {
                    throw new IllegalArgumentException("server must be at least 1 characters");
                }
            }
            if (occurredAt != null) {
                Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("target", target.toPayload());
            payload.put("actor", actor.toPayload());
            payload.put("reason", reason);
            if (expiration != null) {
                payload.put("expiration", expiration.toPayload());
            }
            if (server != null) {
                payload.put("server", server);
            }
            if (occurredAt != null) {
                payload.put("occurredAt", occurredAt);
            }
            return payload;
        }
    }

    public record ModerationPardonCommandV1(
            PlayerCommandTargetV1 target,
            String server,
            String requestedAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "moderation.pardon.command";
        public static final int MESSAGE_VERSION = 1;

        public ModerationPardonCommandV1 {
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            if (requestedAt != null) {
                Objects.requireNonNull(requestedAt, "requestedAt must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("target", target.toPayload());
            payload.put("server", server);
            if (requestedAt != null) {
                payload.put("requestedAt", requestedAt);
            }
            return payload;
        }
    }

    public record ModerationVoteKickCreatedV1(
            PlayerRefV1 target,
            ActorRefV1 actor,
            String reason,
            List<VoteKickParticipantV1> votesFor,
            List<VoteKickParticipantV1> votesAgainst,
            String server,
            String occurredAt
    ) implements ProtocolPayload {
        public static final String MESSAGE_TYPE = "moderation.vote-kick.created";
        public static final int MESSAGE_VERSION = 1;

        public ModerationVoteKickCreatedV1 {
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(actor, "actor must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (reason.length() < 1) {
                throw new IllegalArgumentException("reason must be at least 1 characters");
            }
            if (votesFor != null) {
                votesFor = Objects.requireNonNull(votesFor, "votesFor must not be null");
                votesFor = List.copyOf(votesFor);
                for (VoteKickParticipantV1 item : votesFor) {
                    Objects.requireNonNull(item, "votesFor[] must not be null");
                }
            }
            if (votesAgainst != null) {
                votesAgainst = Objects.requireNonNull(votesAgainst, "votesAgainst must not be null");
                votesAgainst = List.copyOf(votesAgainst);
                for (VoteKickParticipantV1 item : votesAgainst) {
                    Objects.requireNonNull(item, "votesAgainst[] must not be null");
                }
            }
            if (server != null) {
                Objects.requireNonNull(server, "server must not be null");
                if (server.length() < 1) {
                    throw new IllegalArgumentException("server must be at least 1 characters");
                }
            }
            if (occurredAt != null) {
                Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("target", target.toPayload());
            payload.put("actor", actor.toPayload());
            payload.put("reason", reason);
            if (votesFor != null) {
                payload.put(
        "votesFor",
        votesFor.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            if (votesAgainst != null) {
                payload.put(
        "votesAgainst",
        votesAgainst.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            if (server != null) {
                payload.put("server", server);
            }
            if (occurredAt != null) {
                payload.put("occurredAt", occurredAt);
            }
            return payload;
        }
    }
}
