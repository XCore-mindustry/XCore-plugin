package org.xcore.plugin.service.moderation;

import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditAppendCommand;
import org.xcore.plugin.model.AuditAppendResult;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.Slice;

import java.util.Optional;

public interface AuditService {

    AuditAppendResult append(AuditAppendCommand command);

    Slice<AuditRecord> findByTargetUuid(String targetUuid, AuditCursor cursor, int limit);

    Slice<AuditRecordSummary> findSummaryByTargetUuid(String targetUuid, AuditCursor cursor, int limit);

    Slice<AuditRecord> findByActor(AuditActorType actorType, String actorId, AuditCursor cursor, int limit);

    Slice<AuditRecordSummary> findSummaryByActor(AuditActorType actorType, String actorId, AuditCursor cursor, int limit);

    Slice<AuditRecord> findGlobal(AuditCursor cursor, int limit);

    Optional<AuditRecord> findByAuditId(String auditId);
}
