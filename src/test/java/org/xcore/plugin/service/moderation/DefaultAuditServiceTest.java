package org.xcore.plugin.service.moderation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.AuditRecordRepository;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditAppendCommand;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditOrigin;
import org.xcore.plugin.model.AuditOriginChannel;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.AuditTarget;
import org.xcore.plugin.model.Slice;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAuditServiceTest {

    private AuditRecordRepository repository;
    private DefaultAuditService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditRecordRepository.class);
        Config config = new Config();
        config.server = "mini-pvp";
        service = new DefaultAuditService(repository, config);
    }

    @Test
    @DisplayName("append fails when command is invalid")
    void appendFailsWhenCommandIsInvalid() {
        var result = service.append(null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Invalid audit command");
    }

    @Test
    @DisplayName("append normalizes defaults and persists record")
    void appendNormalizesDefaultsAndPersistsRecord() {
        when(repository.save(any())).thenReturn(true);

        AuditTarget target = new AuditTarget();
        target.uuid = "target-1";
        target.nameSnapshot = "Target";

        AuditActor actor = new AuditActor();
        actor.type = AuditActorType.DISCORD_USER;
        actor.id = "discord-1";
        actor.nameSnapshot = "Moderator";

        var result = service.append(new AuditAppendCommand(
                AuditAction.BAN,
                target,
                actor,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRecord()).isPresent();

        verify(repository).save(any(AuditRecord.class));

        AuditRecord saved = result.getRecord().orElseThrow();
        assertThat(saved.auditId).isNotBlank();
        assertThat(saved.category).isEqualTo("SANCTION");
        assertThat(saved.reason).isEqualTo("Not Specified");
        assertThat(saved.origin).isNotNull();
        assertThat(saved.origin.channel).isEqualTo(AuditOriginChannel.SYSTEM);
        assertThat(saved.origin.source).isEqualTo("xcore-plugin");
        assertThat(saved.origin.serverId).isEqualTo("mini-pvp");
        assertThat(saved.origin.requestId).isNotBlank();
        assertThat(saved.integrity).isNotNull();
        assertThat(saved.integrity.dedupeKey).isEqualTo("BAN:discord-1:target-1:");
        assertThat(saved.createdAtEpochMs).isPositive();
        assertThat(saved.createdAt).isNotNull();
        assertThat(saved.occurredAt).isNotNull();
    }

    @Test
    @DisplayName("append keeps explicit origin and reason")
    void appendKeepsExplicitOriginAndReason() {
        when(repository.save(any())).thenReturn(true);

        AuditTarget target = new AuditTarget();
        target.uuid = "target-2";

        AuditOrigin origin = new AuditOrigin();
        origin.channel = AuditOriginChannel.DISCORD;
        origin.source = "xcore-discord-bot";
        origin.serverId = "discord-gateway";
        origin.requestId = "req-123";

        var result = service.append(new AuditAppendCommand(
                AuditAction.NOTE,
                target,
                null,
                origin,
                "reason",
                null,
                null,
                null,
                "req-override"
        ));

        AuditRecord saved = result.getRecord().orElseThrow();
        assertThat(saved.category).isEqualTo("NOTE");
        assertThat(saved.reason).isEqualTo("reason");
        assertThat(saved.origin.channel).isEqualTo(AuditOriginChannel.DISCORD);
        assertThat(saved.origin.source).isEqualTo("xcore-discord-bot");
        assertThat(saved.origin.serverId).isEqualTo("discord-gateway");
        assertThat(saved.origin.requestId).isEqualTo("req-123");
    }

    @Test
    @DisplayName("append returns failure when repository save fails")
    void appendReturnsFailureWhenRepositorySaveFails() {
        when(repository.save(any())).thenReturn(false);

        AuditTarget target = new AuditTarget();
        target.uuid = "target-3";

        var result = service.append(new AuditAppendCommand(
                AuditAction.WARN,
                target,
                null,
                null,
                "spam",
                null,
                null,
                null,
                null
        ));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Failed to append audit record");
    }

    @Test
    @DisplayName("read methods delegate to repository")
    void readMethodsDelegateToRepository() {
        AuditCursor cursor = new AuditCursor(10L, "audit-10");
        Slice<AuditRecord> records = new Slice<>(List.of(new AuditRecord()), false, null);
        Slice<AuditRecordSummary> summaries = new Slice<>(List.of(new AuditRecordSummary("a", AuditAction.BAN, "t", "a", "r", null, null, 1L)), false, null);
        AuditRecord record = new AuditRecord();
        record.auditId = "audit-99";

        when(repository.findByTargetUuid("target-9", cursor, 5)).thenReturn(records);
        when(repository.findSummaryByTargetUuid("target-9", cursor, 5)).thenReturn(summaries);
        when(repository.findByActor(AuditActorType.SYSTEM, "system:automod", cursor, 5)).thenReturn(records);
        when(repository.findGlobal(cursor, 5)).thenReturn(records);
        when(repository.findByAuditId("audit-99")).thenReturn(Optional.of(record));

        assertThat(service.findByTargetUuid("target-9", cursor, 5)).isSameAs(records);
        assertThat(service.findSummaryByTargetUuid("target-9", cursor, 5)).isSameAs(summaries);
        assertThat(service.findByActor(AuditActorType.SYSTEM, "system:automod", cursor, 5)).isSameAs(records);
        assertThat(service.findGlobal(cursor, 5)).isSameAs(records);
        assertThat(service.findByAuditId("audit-99")).contains(record);

        verify(repository).findByTargetUuid("target-9", cursor, 5);
        verify(repository).findSummaryByTargetUuid("target-9", cursor, 5);
        verify(repository).findByActor(AuditActorType.SYSTEM, "system:automod", cursor, 5);
        verify(repository).findGlobal(cursor, 5);
        verify(repository).findByAuditId(eq("audit-99"));
    }
}
