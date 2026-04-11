package org.xcore.plugin.database.repository;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditRecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditRecordRepositoryLogicTest {

    private AuditRecordRepository repository;

    @BeforeEach
    void setUp() {
        MongoDatabase database = mock(MongoDatabase.class);
        @SuppressWarnings("unchecked")
        MongoCollection<AuditRecord> collection = mock(MongoCollection.class);
        when(database.getCollection("moderation_audit", AuditRecord.class)).thenReturn(collection);

        repository = new AuditRecordRepository(database, new GlobalConfig());
    }

    @Test
    @DisplayName("cursorFilter returns null when cursor is missing")
    void cursorFilterReturnsNullWhenCursorMissing() {
        assertThat(AuditRecordRepository.cursorFilter(null)).isNull();
    }

    @Test
    @DisplayName("cursorFilter sorts by timestamp then audit id")
    void cursorFilterSortsByTimestampThenAuditId() {
        Bson filter = AuditRecordRepository.cursorFilter(new AuditCursor(100L, "audit-2"));
        String json = toJson(filter);

        assertThat(json).contains("\"$or\"");
        assertThat(json).contains("\"created_at_epoch_ms\": {\"$lt\": 100}");
        assertThat(json).contains("\"created_at_epoch_ms\": 100");
        assertThat(json).contains("\"audit_id\": {\"$lt\": \"audit-2\"}");
    }

    @Test
    @DisplayName("summaryProjection includes only summary fields")
    void summaryProjectionIncludesOnlySummaryFields() {
        String json = toJson(AuditRecordRepository.summaryProjection());

        assertThat(json).contains("\"audit_id\"");
        assertThat(json).contains("\"action\"");
        assertThat(json).contains("\"target.name_snapshot\"");
        assertThat(json).contains("\"actor.name_snapshot\"");
        assertThat(json).contains("\"details.duration_ms\"");
        assertThat(json).contains("\"details.expires_at\"");
    }

    private static String toJson(Bson query) {
        return query.toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry()).toJson();
    }
}
