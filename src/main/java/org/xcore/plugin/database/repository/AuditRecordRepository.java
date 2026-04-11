package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.Slice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Filters.or;

@Singleton
public class AuditRecordRepository extends DataRepository<AuditRecord> {

    private static final int DEFAULT_LIMIT = 20;

    @Inject
    public AuditRecordRepository(MongoDatabase database, GlobalConfig globalConfig) {
        super(database, "moderation_audit", AuditRecord.class, globalConfig);

        collection.createIndex(new Document("target.uuid", 1)
                .append("created_at_epoch_ms", -1)
                .append("audit_id", -1));
        collection.createIndex(new Document("actor.type", 1)
                .append("actor.id", 1)
                .append("created_at_epoch_ms", -1)
                .append("audit_id", -1));
        collection.createIndex(new Document("created_at_epoch_ms", -1)
                .append("audit_id", -1));
        collection.createIndex(new Document("action", 1)
                .append("created_at_epoch_ms", -1)
                .append("audit_id", -1));
        collection.createIndex(new Document("origin.server_id", 1)
                .append("created_at_epoch_ms", -1)
                .append("audit_id", -1));
        collection.createIndex(new Document("origin.request_id", 1));
        collection.createIndex(new Document("related_audit_id", 1));
        collection.createIndex(new Document("supersedes_audit_id", 1));
        collection.createIndex(new Document("integrity.dedupe_key", 1));
    }

    public Optional<AuditRecord> findByAuditId(String auditId) {
        if (auditId == null || auditId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(collection.find(eq("audit_id", auditId)).first());
    }

    public Slice<AuditRecord> findByTargetUuid(String targetUuid, AuditCursor cursor, int limit) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return new Slice<>(List.of(), false, null);
        }
        return readSlice(eq("target.uuid", targetUuid), cursor, limit);
    }

    public Slice<AuditRecord> findByActor(AuditActorType actorType, String actorId, AuditCursor cursor, int limit) {
        if (actorType == null || actorId == null || actorId.isBlank()) {
            return new Slice<>(List.of(), false, null);
        }
        return readSlice(and(eq("actor.type", actorType), eq("actor.id", actorId)), cursor, limit);
    }

    public Slice<AuditRecord> findGlobal(AuditCursor cursor, int limit) {
        return readSlice(new Document(), cursor, limit);
    }

    public Slice<AuditRecordSummary> findSummaryByTargetUuid(String targetUuid, AuditCursor cursor, int limit) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return new Slice<>(List.of(), false, null);
        }
        return readSummarySlice(eq("target.uuid", targetUuid), cursor, limit);
    }

    static Bson cursorFilter(AuditCursor cursor) {
        if (cursor == null || cursor.auditId() == null || cursor.auditId().isBlank()) {
            return null;
        }
        return or(
                lt("created_at_epoch_ms", cursor.createdAtEpochMs()),
                and(
                        eq("created_at_epoch_ms", cursor.createdAtEpochMs()),
                        lt("audit_id", cursor.auditId())
                )
        );
    }

    private Slice<AuditRecord> readSlice(Bson baseFilter, AuditCursor cursor, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        Bson effectiveFilter = combineFilters(baseFilter, cursorFilter(cursor));

        var data = collection.find(effectiveFilter)
                .sort(new Document("created_at_epoch_ms", -1).append("audit_id", -1))
                .limit(normalizedLimit + 1)
                .into(new ArrayList<>());

        boolean hasNext = data.size() > normalizedLimit;
        if (hasNext) {
            data.removeLast();
        }

        AuditCursor nextCursor = null;
        if (hasNext && !data.isEmpty()) {
            AuditRecord last = data.getLast();
            nextCursor = new AuditCursor(last.createdAtEpochMs, last.auditId);
        }

        return new Slice<>(List.copyOf(data), hasNext, nextCursor);
    }

    private Slice<AuditRecordSummary> readSummarySlice(Bson baseFilter, AuditCursor cursor, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        Bson effectiveFilter = combineFilters(baseFilter, cursorFilter(cursor));

        var documents = collection.withDocumentClass(Document.class)
                .find(effectiveFilter)
                .projection(summaryProjection())
                .sort(new Document("created_at_epoch_ms", -1).append("audit_id", -1))
                .limit(normalizedLimit + 1)
                .into(new ArrayList<>());

        boolean hasNext = documents.size() > normalizedLimit;
        if (hasNext) {
            documents.removeLast();
        }

        List<AuditRecordSummary> items = documents.stream()
                .map(AuditRecordRepository::toSummary)
                .toList();

        AuditCursor nextCursor = null;
        if (hasNext && !items.isEmpty()) {
            AuditRecordSummary last = items.getLast();
            nextCursor = new AuditCursor(last.createdAtEpochMs(), last.auditId());
        }

        return new Slice<>(items, hasNext, nextCursor);
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, 100);
    }

    static Bson summaryProjection() {
        return Projections.fields(
                Projections.include(
                        "audit_id",
                        "action",
                        "target.name_snapshot",
                        "actor.name_snapshot",
                        "reason",
                        "details.duration_ms",
                        "details.expires_at",
                        "created_at_epoch_ms"
                ),
                Projections.excludeId()
        );
    }

    private static AuditRecordSummary toSummary(Document document) {
        Document target = document.get("target", Document.class);
        Document actor = document.get("actor", Document.class);
        Document details = document.get("details", Document.class);

        String auditId = document.getString("audit_id");
        String actionValue = document.getString("action");
        AuditAction action = actionValue == null ? AuditAction.NOTE : AuditAction.valueOf(actionValue);
        String targetName = target == null ? "Unknown" : target.getString("name_snapshot");
        String actorName = actor == null ? "Unknown" : actor.getString("name_snapshot");
        String reason = document.getString("reason");
        Long durationMs = details == null ? null : extractLong(details.get("duration_ms"));
        Instant expiresAt = details == null ? null : extractInstant(details.get("expires_at"));
        Long createdAtEpochMs = extractLong(document.get("created_at_epoch_ms"));

        return new AuditRecordSummary(
                auditId,
                action,
                targetName == null ? "Unknown" : targetName,
                actorName == null ? "Unknown" : actorName,
                reason == null ? "Not Specified" : reason,
                durationMs,
                expiresAt,
                createdAtEpochMs == null ? 0L : createdAtEpochMs
        );
    }

    private static Long extractLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static Instant extractInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Instant.parse(stringValue);
        }
        return null;
    }

    private static Bson combineFilters(Bson baseFilter, Bson cursorFilter) {
        if (cursorFilter == null) {
            return baseFilter;
        }
        return and(baseFilter, cursorFilter);
    }
}
