package org.xcore.plugin.model;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AuditRecord extends ModelData {
    @BsonProperty("audit_id")
    public String auditId;

    @BsonProperty("schema_version")
    @Builder.Default
    public int schemaVersion = 1;

    @Builder.Default
    public AuditAction action = AuditAction.NOTE;

    @Builder.Default
    public String category = "NOTE";

    @Builder.Default
    public AuditTarget target = new AuditTarget();

    @Builder.Default
    public AuditActor actor = new AuditActor();

    @Builder.Default
    public AuditOrigin origin = new AuditOrigin();

    @Builder.Default
    public String reason = "Not Specified";

    @Builder.Default
    public AuditDetails details = new AuditDetails();

    @BsonProperty("related_audit_id")
    public String relatedAuditId;

    @BsonProperty("supersedes_audit_id")
    public String supersedesAuditId;

    @BsonProperty("occurred_at")
    public Instant occurredAt;

    @BsonProperty("created_at_ts")
    @Builder.Default
    public Instant createdAt = Instant.now();

    @BsonProperty("created_at_epoch_ms")
    @Builder.Default
    public long createdAtEpochMs = System.currentTimeMillis();

    @Builder.Default
    public AuditIntegrity integrity = new AuditIntegrity();
}
