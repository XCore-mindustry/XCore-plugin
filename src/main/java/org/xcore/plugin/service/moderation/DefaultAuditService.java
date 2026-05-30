package org.xcore.plugin.service.moderation;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.AuditRecordRepository;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditAppendCommand;
import org.xcore.plugin.model.AuditAppendResult;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditDetails;
import org.xcore.plugin.model.AuditIntegrity;
import org.xcore.plugin.model.AuditOrigin;
import org.xcore.plugin.model.AuditOriginChannel;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.Slice;

import java.util.List;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultAuditService implements AuditService {
    private static final String DEFAULT_REASON = "Not Specified";
    private static final String DEFAULT_SOURCE = "xcore-plugin";

    private final AuditRecordRepository repository;
    private final TomlXcoreConfig config;

    @Inject
    public DefaultAuditService(AuditRecordRepository repository, TomlXcoreConfig config) {
        this.repository = repository;
        this.config = config;
    }

    @Override
    public AuditAppendResult append(AuditAppendCommand command) {
        if (command == null || command.action() == null || command.target() == null) {
            return AuditAppendResult.failure("Invalid audit command");
        }

        Instant now = Instant.now();
        String auditId = UUID.randomUUID().toString();

        AuditOrigin origin = normalizeOrigin(command.origin(), command.requestId());
        AuditDetails details = command.details() == null ? new AuditDetails() : command.details();

        AuditRecord record = new AuditRecord();
        record.auditId = auditId;
        record.action = command.action();
        record.category = categoryFor(command.action());
        record.target = command.target();
        record.actor = command.actor();
        record.origin = origin;
        record.reason = command.reason() == null || command.reason().isBlank() ? DEFAULT_REASON : command.reason();
        record.details = details;
        record.relatedAuditId = command.relatedAuditId();
        record.supersedesAuditId = command.supersedesAuditId();
        record.occurredAt = now;
        record.createdAt = now;
        record.createdAtEpochMs = now.toEpochMilli();
        record.integrity = new AuditIntegrity();
        record.integrity.dedupeKey = buildDedupeKey(command);
        record.integrity.hash = null;

        if (!repository.save(record)) {
            return AuditAppendResult.failure("Failed to append audit record");
        }

        return AuditAppendResult.success(record);
    }

    @Override
    public Slice<AuditRecord> findByTargetUuid(String targetUuid, AuditCursor cursor, int limit) {
        return repository.findByTargetUuid(targetUuid, cursor, limit);
    }

    @Override
    public Slice<AuditRecordSummary> findSummaryByTargetUuid(String targetUuid, AuditCursor cursor, int limit) {
        return repository.findSummaryByTargetUuid(targetUuid, cursor, limit);
    }

    @Override
    public Slice<AuditRecord> findByActor(AuditActorType actorType, String actorId, AuditCursor cursor, int limit) {
        return repository.findByActor(actorType, actorId, cursor, limit);
    }

    @Override
    public Slice<AuditRecordSummary> findSummaryByActor(AuditActorType actorType, String actorId, AuditCursor cursor, int limit) {
        return repository.findSummaryByActor(actorType, actorId, cursor, limit);
    }

    public Slice<AuditRecordSummary> findSummaryByActor(AuditActorType actorType, List<String> actorIds, AuditCursor cursor, int limit) {
        return repository.findSummaryByActor(actorType, actorIds, cursor, limit);
    }

    @Override
    public Slice<AuditRecord> findGlobal(AuditCursor cursor, int limit) {
        return repository.findGlobal(cursor, limit);
    }

    @Override
    public Optional<AuditRecord> findByAuditId(String auditId) {
        return repository.findByAuditId(auditId);
    }

    private AuditOrigin normalizeOrigin(AuditOrigin origin, String requestId) {
        AuditOrigin value = origin == null ? new AuditOrigin() : origin;
        if (value.source == null || value.source.isBlank()) {
            value.source = DEFAULT_SOURCE;
        }
        if (value.serverId == null || value.serverId.isBlank()) {
            value.serverId = config.server.name;
        }
        if (value.channel == null) {
            value.channel = AuditOriginChannel.SYSTEM;
        }
        if (value.requestId == null || value.requestId.isBlank()) {
            value.requestId = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
        }
        return value;
    }

    private static String categoryFor(AuditAction action) {
        return switch (action) {
            case NOTE -> "NOTE";
            case WARN, KICK -> "CONTROL";
            default -> "SANCTION";
        };
    }

    private static String buildDedupeKey(AuditAppendCommand command) {
        String actorId = command.actor() == null ? "" : String.valueOf(command.actor().id);
        String targetUuid = command.target() == null ? "" : String.valueOf(command.target().uuid);
        String reason = command.reason() == null ? "" : command.reason().trim();
        return command.action() + ":" + actorId + ":" + targetUuid + ":" + reason;
    }
}
