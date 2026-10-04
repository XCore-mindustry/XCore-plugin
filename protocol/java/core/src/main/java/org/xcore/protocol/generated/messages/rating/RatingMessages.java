package org.xcore.protocol.generated.messages.rating;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;
import org.xcore.protocol.generated.shared.ActorRefV1;
import org.xcore.protocol.generated.shared.SeasonPodiumEntryV1;
import org.xcore.protocol.generated.shared.SeasonPrizeV1;
import org.xcore.protocol.generated.shared.SeasonRefV1;
import org.xcore.protocol.generated.shared.SeasonSummaryV1;

public final class RatingMessages {
    private RatingMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.rating.RatingMessages instances");
    }

    public record RatingAccountsMergeRequestV1(
            String server,
            String sourceUuid,
            String targetUuid
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.accounts.merge.request";
    public static final int MESSAGE_VERSION = 1;

        public RatingAccountsMergeRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(sourceUuid, "sourceUuid must not be null");
            if (sourceUuid.length() < 1) {
                throw new IllegalArgumentException("sourceUuid must be at least 1 characters");
            }
            Objects.requireNonNull(targetUuid, "targetUuid must not be null");
            if (targetUuid.length() < 1) {
                throw new IllegalArgumentException("targetUuid must be at least 1 characters");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("sourceUuid", sourceUuid);
            payload.put("targetUuid", targetUuid);
            return payload;
        }
    }

    public record RatingAccountsMergeResponseV1(
            String server,
            int standingsMerged
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.accounts.merge.response";
    public static final int MESSAGE_VERSION = 1;

        public RatingAccountsMergeResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            if (standingsMerged < 0) {
                throw new IllegalArgumentException("standingsMerged must be >= 0");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("standingsMerged", standingsMerged);
            return payload;
        }
    }

    public record RatingPrizeGrantUpdateRequestV1(
            String server,
            String ladder,
            int season,
            int place,
            Integer playerPid,
            RatingPrizeGrantUpdateRequestV1Status status,
            ActorRefV1 actor,
            String note
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.prize.grant.update.request";
    public static final int MESSAGE_VERSION = 1;

        public RatingPrizeGrantUpdateRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(ladder, "ladder must not be null");
            if (ladder.length() < 1) {
                throw new IllegalArgumentException("ladder must be at least 1 characters");
            }
            if (season < 1) {
                throw new IllegalArgumentException("season must be >= 1");
            }
            if (place < 1) {
                throw new IllegalArgumentException("place must be >= 1");
            }
            if (playerPid != null) {
                if (playerPid < 1) {
                    throw new IllegalArgumentException("playerPid must be >= 1");
                }
            }
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(actor, "actor must not be null");
            if (note != null) {
                Objects.requireNonNull(note, "note must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("ladder", ladder);
            payload.put("season", season);
            payload.put("place", place);
            if (playerPid != null) {
                payload.put("playerPid", playerPid);
            }
            if (status != null) {
                payload.put("status", status.toString());
            }
            payload.put("actor", actor.toPayload());
            if (note != null) {
                payload.put("note", note);
            }
            return payload;
        }
    }

    public record RatingPrizeGrantUpdateResponseV1(
            String server,
            int season,
            int place,
            int updated
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.prize.grant.update.response";
    public static final int MESSAGE_VERSION = 1;

        public RatingPrizeGrantUpdateResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            if (season < 1) {
                throw new IllegalArgumentException("season must be >= 1");
            }
            if (place < 1) {
                throw new IllegalArgumentException("place must be >= 1");
            }
            if (updated < 0) {
                throw new IllegalArgumentException("updated must be >= 0");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("season", season);
            payload.put("place", place);
            payload.put("updated", updated);
            return payload;
        }
    }

    public record RatingSeasonEndedV1(
            SeasonRefV1 season,
            List<SeasonPodiumEntryV1> podium,
            SeasonSummaryV1 summary,
            String server,
            String occurredAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.season.ended";
    public static final int MESSAGE_VERSION = 1;

        public RatingSeasonEndedV1 {
            Objects.requireNonNull(season, "season must not be null");
            podium = Objects.requireNonNull(podium, "podium must not be null");
            podium = List.copyOf(podium);
            for (SeasonPodiumEntryV1 item : podium) {
                Objects.requireNonNull(item, "podium[] must not be null");
            }
            Objects.requireNonNull(summary, "summary must not be null");
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("season", season.toPayload());
            if (podium != null) {
                payload.put(
        "podium",
        podium.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            payload.put("summary", summary.toPayload());
            payload.put("server", server);
            payload.put("occurredAt", occurredAt);
            return payload;
        }
    }

    public record RatingSeasonEndingSoonV1(
            SeasonRefV1 season,
            String notice,
            String server,
            String occurredAt,
            List<SeasonPrizeV1> prizes
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.season.ending-soon";
    public static final int MESSAGE_VERSION = 1;

        public RatingSeasonEndingSoonV1 {
            Objects.requireNonNull(season, "season must not be null");
            Objects.requireNonNull(notice, "notice must not be null");
            if (notice.length() < 1) {
                throw new IllegalArgumentException("notice must be at least 1 characters");
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            if (prizes != null) {
                prizes = Objects.requireNonNull(prizes, "prizes must not be null");
                prizes = List.copyOf(prizes);
                for (SeasonPrizeV1 item : prizes) {
                    Objects.requireNonNull(item, "prizes[] must not be null");
                }
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("season", season.toPayload());
            payload.put("notice", notice);
            payload.put("server", server);
            payload.put("occurredAt", occurredAt);
            if (prizes != null) {
                payload.put(
        "prizes",
        prizes.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            return payload;
        }
    }

    public record RatingSeasonPrizesSetRequestV1(
            String server,
            String ladder,
            RatingSeasonPrizesSetRequestV1Operation operation,
            SeasonPrizeV1 prize,
            Integer placeFrom,
            Integer placeTo,
            ActorRefV1 actor
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.season.prizes.set.request";
    public static final int MESSAGE_VERSION = 1;

        public RatingSeasonPrizesSetRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(ladder, "ladder must not be null");
            if (ladder.length() < 1) {
                throw new IllegalArgumentException("ladder must be at least 1 characters");
            }
            Objects.requireNonNull(operation, "operation must not be null");
            if (prize != null) {
                Objects.requireNonNull(prize, "prize must not be null");
            }
            if (placeFrom != null) {
                if (placeFrom < 1) {
                    throw new IllegalArgumentException("placeFrom must be >= 1");
                }
            }
            if (placeTo != null) {
                if (placeTo < 1) {
                    throw new IllegalArgumentException("placeTo must be >= 1");
                }
            }
            Objects.requireNonNull(actor, "actor must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("ladder", ladder);
            if (operation != null) {
                payload.put("operation", operation.toString());
            }
            if (prize != null) {
                payload.put("prize", prize.toPayload());
            }
            if (placeFrom != null) {
                payload.put("placeFrom", placeFrom);
            }
            if (placeTo != null) {
                payload.put("placeTo", placeTo);
            }
            payload.put("actor", actor.toPayload());
            return payload;
        }
    }

    public record RatingSeasonPrizesSetResponseV1(
            String server,
            SeasonRefV1 season,
            List<SeasonPrizeV1> prizes
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.season.prizes.set.response";
    public static final int MESSAGE_VERSION = 1;

        public RatingSeasonPrizesSetResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(season, "season must not be null");
            prizes = Objects.requireNonNull(prizes, "prizes must not be null");
            prizes = List.copyOf(prizes);
            for (SeasonPrizeV1 item : prizes) {
                Objects.requireNonNull(item, "prizes[] must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("season", season.toPayload());
            if (prizes != null) {
                payload.put(
        "prizes",
        prizes.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            return payload;
        }
    }

    public record RatingSeasonRescheduleRequestV1(
            String server,
            String ladder,
            RatingSeasonRescheduleRequestV1Operation operation,
            Integer extendSeconds,
            String endsAt,
            ActorRefV1 actor,
            String reason
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.season.reschedule.request";
    public static final int MESSAGE_VERSION = 1;

        public RatingSeasonRescheduleRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(ladder, "ladder must not be null");
            if (ladder.length() < 1) {
                throw new IllegalArgumentException("ladder must be at least 1 characters");
            }
            Objects.requireNonNull(operation, "operation must not be null");
            if (extendSeconds != null) {
                if (extendSeconds < 1) {
                    throw new IllegalArgumentException("extendSeconds must be >= 1");
                }
            }
            if (endsAt != null) {
                Objects.requireNonNull(endsAt, "endsAt must not be null");
            }
            Objects.requireNonNull(actor, "actor must not be null");
            if (reason != null) {
                Objects.requireNonNull(reason, "reason must not be null");
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("ladder", ladder);
            if (operation != null) {
                payload.put("operation", operation.toString());
            }
            if (extendSeconds != null) {
                payload.put("extendSeconds", extendSeconds);
            }
            if (endsAt != null) {
                payload.put("endsAt", endsAt);
            }
            payload.put("actor", actor.toPayload());
            if (reason != null) {
                payload.put("reason", reason);
            }
            return payload;
        }
    }

    public record RatingSeasonRescheduleResponseV1(
            String server,
            SeasonRefV1 season,
            String previousEndsAt,
            boolean ended
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.season.reschedule.response";
    public static final int MESSAGE_VERSION = 1;

        public RatingSeasonRescheduleResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(season, "season must not be null");
            Objects.requireNonNull(previousEndsAt, "previousEndsAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("season", season.toPayload());
            payload.put("previousEndsAt", previousEndsAt);
            payload.put("ended", ended);
            return payload;
        }
    }

    public record RatingSeasonRescheduledV1(
            SeasonRefV1 season,
            String previousEndsAt,
            ActorRefV1 actor,
            String reason,
            String server,
            String occurredAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.season.rescheduled";
    public static final int MESSAGE_VERSION = 1;

        public RatingSeasonRescheduledV1 {
            Objects.requireNonNull(season, "season must not be null");
            Objects.requireNonNull(previousEndsAt, "previousEndsAt must not be null");
            Objects.requireNonNull(actor, "actor must not be null");
            if (reason != null) {
                Objects.requireNonNull(reason, "reason must not be null");
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("season", season.toPayload());
            payload.put("previousEndsAt", previousEndsAt);
            payload.put("actor", actor.toPayload());
            if (reason != null) {
                payload.put("reason", reason);
            }
            payload.put("server", server);
            payload.put("occurredAt", occurredAt);
            return payload;
        }
    }

    public record RatingSeasonStartedV1(
            SeasonRefV1 season,
            Integer previousSeason,
            String server,
            String occurredAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "rating.season.started";
    public static final int MESSAGE_VERSION = 1;

        public RatingSeasonStartedV1 {
            Objects.requireNonNull(season, "season must not be null");
            if (previousSeason != null) {
                if (previousSeason < 1) {
                    throw new IllegalArgumentException("previousSeason must be >= 1");
                }
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("season", season.toPayload());
            if (previousSeason != null) {
                payload.put("previousSeason", previousSeason);
            }
            payload.put("server", server);
            payload.put("occurredAt", occurredAt);
            return payload;
        }
    }
}
