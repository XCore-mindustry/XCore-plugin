package org.xcore.protocol.generated.messages.cases;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;
import org.xcore.protocol.generated.shared.CaseBlockSummaryV1;
import org.xcore.protocol.generated.shared.CaseChatLineV1;
import org.xcore.protocol.generated.shared.PlayerRefV1;

public final class CasesMessages {
    private CasesMessages() {
        throw new AssertionError("No org.xcore.protocol.generated.messages.cases.CasesMessages instances");
    }

    public record CasesEvidenceRequestV1(
            String server,
            String playerUuid,
            Integer chatLines,
            Integer windowSeconds
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "cases.evidence.request";
    public static final int MESSAGE_VERSION = 1;

        public CasesEvidenceRequestV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
            }
            if (chatLines != null) {
                if (chatLines < 1) {
                    throw new IllegalArgumentException("chatLines must be >= 1");
                }
            }
            if (windowSeconds != null) {
                if (windowSeconds < 60) {
                    throw new IllegalArgumentException("windowSeconds must be >= 60");
                }
            }
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("playerUuid", playerUuid);
            if (chatLines != null) {
                payload.put("chatLines", chatLines);
            }
            if (windowSeconds != null) {
                payload.put("windowSeconds", windowSeconds);
            }
            return payload;
        }
    }

    public record CasesEvidenceResponseV1(
            String server,
            boolean targetOnline,
            String mapName,
            String gameMode,
            List<CaseChatLineV1> chat,
            CaseBlockSummaryV1 blockSummary,
            String capturedAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "cases.evidence.response";
    public static final int MESSAGE_VERSION = 1;

        public CasesEvidenceResponseV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            if (mapName != null) {
                Objects.requireNonNull(mapName, "mapName must not be null");
            }
            if (gameMode != null) {
                Objects.requireNonNull(gameMode, "gameMode must not be null");
            }
            chat = Objects.requireNonNull(chat, "chat must not be null");
            chat = List.copyOf(chat);
            for (CaseChatLineV1 item : chat) {
                Objects.requireNonNull(item, "chat[] must not be null");
            }
            if (blockSummary != null) {
                Objects.requireNonNull(blockSummary, "blockSummary must not be null");
            }
            Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("server", server);
            payload.put("targetOnline", targetOnline);
            if (mapName != null) {
                payload.put("mapName", mapName);
            }
            if (gameMode != null) {
                payload.put("gameMode", gameMode);
            }
            if (chat != null) {
                payload.put(
        "chat",
        chat.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            if (blockSummary != null) {
                payload.put("blockSummary", blockSummary.toPayload());
            }
            payload.put("capturedAt", capturedAt);
            return payload;
        }
    }

    public record CasesNoticeCommandV1(
            String server,
            String noticeId,
            String playerUuid,
            String requestedAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "cases.notice.command";
    public static final int MESSAGE_VERSION = 1;

        public CasesNoticeCommandV1 {
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(noticeId, "noticeId must not be null");
            if (noticeId.length() < 1) {
                throw new IllegalArgumentException("noticeId must be at least 1 characters");
            }
            Objects.requireNonNull(playerUuid, "playerUuid must not be null");
            if (playerUuid.length() < 1) {
                throw new IllegalArgumentException("playerUuid must be at least 1 characters");
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
            payload.put("server", server);
            payload.put("noticeId", noticeId);
            payload.put("playerUuid", playerUuid);
            if (requestedAt != null) {
                payload.put("requestedAt", requestedAt);
            }
            return payload;
        }
    }

    public record CasesReportSubmittedV1(
            String submissionId,
            String server,
            PlayerRefV1 reporter,
            PlayerRefV1 target,
            boolean targetOnline,
            String reason,
            String comment,
            String reporterLocale,
            String mapName,
            String mapFileName,
            String gameMode,
            Boolean sameNetwork,
            List<CaseChatLineV1> chat,
            CaseBlockSummaryV1 blockSummary,
            String occurredAt
    ) implements ProtocolPayload {
    public static final String MESSAGE_TYPE = "cases.report.submitted";
    public static final int MESSAGE_VERSION = 1;

        public CasesReportSubmittedV1 {
            Objects.requireNonNull(submissionId, "submissionId must not be null");
            if (submissionId.length() < 1) {
                throw new IllegalArgumentException("submissionId must be at least 1 characters");
            }
            Objects.requireNonNull(server, "server must not be null");
            if (server.length() < 1) {
                throw new IllegalArgumentException("server must be at least 1 characters");
            }
            Objects.requireNonNull(reporter, "reporter must not be null");
            Objects.requireNonNull(target, "target must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (comment != null) {
                Objects.requireNonNull(comment, "comment must not be null");
            }
            if (reporterLocale != null) {
                Objects.requireNonNull(reporterLocale, "reporterLocale must not be null");
            }
            if (mapName != null) {
                Objects.requireNonNull(mapName, "mapName must not be null");
            }
            if (mapFileName != null) {
                Objects.requireNonNull(mapFileName, "mapFileName must not be null");
            }
            if (gameMode != null) {
                Objects.requireNonNull(gameMode, "gameMode must not be null");
            }
            chat = Objects.requireNonNull(chat, "chat must not be null");
            chat = List.copyOf(chat);
            for (CaseChatLineV1 item : chat) {
                Objects.requireNonNull(item, "chat[] must not be null");
            }
            if (blockSummary != null) {
                Objects.requireNonNull(blockSummary, "blockSummary must not be null");
            }
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        }

        @Override
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messageType", MESSAGE_TYPE);
            payload.put("messageVersion", MESSAGE_VERSION);
            payload.put("submissionId", submissionId);
            payload.put("server", server);
            payload.put("reporter", reporter.toPayload());
            payload.put("target", target.toPayload());
            payload.put("targetOnline", targetOnline);
            payload.put("reason", reason);
            if (comment != null) {
                payload.put("comment", comment);
            }
            if (reporterLocale != null) {
                payload.put("reporterLocale", reporterLocale);
            }
            if (mapName != null) {
                payload.put("mapName", mapName);
            }
            if (mapFileName != null) {
                payload.put("mapFileName", mapFileName);
            }
            if (gameMode != null) {
                payload.put("gameMode", gameMode);
            }
            if (sameNetwork != null) {
                payload.put("sameNetwork", sameNetwork);
            }
            if (chat != null) {
                payload.put(
        "chat",
        chat.stream()
            .map(item -> item.toPayload())
            .toList()
    );
            }
            if (blockSummary != null) {
                payload.put("blockSummary", blockSummary.toPayload());
            }
            payload.put("occurredAt", occurredAt);
            return payload;
        }
    }
}
