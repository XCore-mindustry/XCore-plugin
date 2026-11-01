package org.xcore.plugin.integration.playerstorage;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerStorageValidationTest {
    private static final PlayerStoreSchema SCHEMA = PlayerStoreSchema.builder()
            .field("score", FieldType.INT, true)
            .field("name", FieldType.STRING, false)
            .build();

    @Test
    void schemaRejectsInvalidAndDuplicateFields() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                PlayerStoreSchema.builder().field("bad.name", FieldType.STRING, false));
        assertThatIllegalArgumentException().isThrownBy(() ->
                PlayerStoreSchema.builder()
                        .field("name", FieldType.STRING, false)
                        .field("name", FieldType.STRING, false));
        assertThatIllegalArgumentException().isThrownBy(() ->
                PlayerStoreSchema.builder().field("name", null, false));
    }

    @Test
    void cursorRoundTripsAllTypesAndSpecialStrings() {
        Map<FieldType, Object> values = Map.of(
                FieldType.INT, 3,
                FieldType.LONG, 4L,
                FieldType.DOUBLE, 4.25d,
                FieldType.BOOLEAN, true,
                FieldType.STRING, "line\nwith.delimiter"
        );
        for (var entry : values.entrySet()) {
            var encoded = PluginPlayerStoreFactory.CursorCodec.encode(
                    entry.getKey(), entry.getValue(), "player\n.uuid", SortDirection.DESC);
            var decoded = PluginPlayerStoreFactory.CursorCodec.decode(encoded);
            assertThat(decoded.type()).isEqualTo(entry.getKey());
            assertThat(decoded.value()).isEqualTo(entry.getValue());
            assertThat(decoded.uuid()).isEqualTo("player\n.uuid");
            assertThat(decoded.direction()).isEqualTo(SortDirection.DESC);
        }
    }

    @Test
    void pageBoundaryUsesLastReturnedRecordAndNotLookahead() {
        var records = List.of("one", "two", "three");

        var boundary = PluginPlayerStoreFactory.MongoPlayerStore.pageBoundary(records, 2);

        assertThat(boundary.records()).containsExactly("one", "two");
        assertThat(boundary.hasNext()).isTrue();
        assertThat(PluginPlayerStoreFactory.MongoPlayerStore.pageBoundary(records, 3).hasNext())
                .isFalse();
        assertThat(PluginPlayerStoreFactory.MongoPlayerStore.pageBoundary(List.of(), 2).records())
                .isEmpty();
    }

    @Test
    void bsonNumbersConvertWithoutTypedGetterAssumptions() {
        assertThat(PluginPlayerStoreFactory.MongoPlayerStore.numberAsLong(Integer.valueOf(7), 0))
                .isEqualTo(7L);
        assertThat(PluginPlayerStoreFactory.MongoPlayerStore.numberAsInt(Long.valueOf(8), 0))
                .isEqualTo(8);
        assertThat(PluginPlayerStoreFactory.MongoPlayerStore.numberAsLong(null, 9)).isEqualTo(9L);
        assertThat(PluginPlayerStoreFactory.MongoPlayerStore.numberAsInt("not a number", 10))
                .isEqualTo(10);
    }

    @Test
    void recordsAndPagesAreImmutable() {
        Map<String, Object> values = new HashMap<>();
        values.put("name", "value");
        var record = new PlayerRecord("player", values, 1, 1);
        values.put("other", "not copied");
        assertThat(record.values()).containsOnlyKeys("name");
        assertThatThrownBy(() ->
                record.values().put("other", "blocked"));

        var source = new java.util.ArrayList<>(List.of(record));
        var page = new PlayerPage(source, "cursor", true);
        source.clear();
        assertThat(page.players()).containsExactly(record);
        assertThatThrownBy(() ->
                page.players().clear());
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerPage(List.of(), null, true));
    }

    @Test
    void deleteReportsWhetherARecordWasDeleted() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        var store = new PluginPlayerStoreFactory.MongoPlayerStore(
                collection, SCHEMA, new TomlSecretsConfig());
        DeleteResult deleted = mock(DeleteResult.class);
        when(collection.deleteOne(any())).thenReturn(deleted);

        when(deleted.getDeletedCount()).thenReturn(0L);
        assertThat(store.delete("missing")).isFalse();
        when(deleted.getDeletedCount()).thenReturn(1L);
        assertThat(store.delete("present")).isTrue();
    }

    @Test
    void writesBuildExpectedUpdatePlans() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        var store = new PluginPlayerStoreFactory.MongoPlayerStore(
                collection, SCHEMA, new TomlSecretsConfig());
        UpdateResult result = mock(UpdateResult.class);
        when(result.wasAcknowledged()).thenReturn(true);
        when(result.getMatchedCount()).thenReturn(1L);
        when(collection.updateOne(any(Bson.class), any(java.util.List.class),
                any(com.mongodb.client.model.UpdateOptions.class))).thenReturn(result);
        ArgumentCaptor<java.util.List> plan = ArgumentCaptor.forClass(java.util.List.class);

        assertThat(store.set("player", "name", "Alice")).isTrue();
        var setPlan = planValue(collection, plan);
        assertThat(setPlan).hasSize(1);
        assertThat(setPlan.get(0).toString()).contains("data.name", "$literal");

        assertThat(store.remove("player", "name")).isTrue();
        var removePlan = planValue(collection, plan);
        assertThat(removePlan).hasSize(2);
        assertThat(removePlan.get(1).toString()).contains("$unset");

        assertThat(store.increment("player", "score", 2)).isTrue();
        var incrementPlan = planValue(collection, plan);
        assertThat(incrementPlan.get(0).toString()).contains("$add", "data.score");
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Document> planValue(MongoCollection<Document> collection,
                                                       ArgumentCaptor<java.util.List> plan) {
        // The pipeline overload is intentionally captured, not executed: Mongo semantics
        // belong to the live-Mongo/integration test suite.
        plan = ArgumentCaptor.forClass(java.util.List.class);
        org.mockito.Mockito.verify(collection, org.mockito.Mockito.atLeastOnce())
                .updateOne(any(Bson.class), plan.capture(),
                        any(com.mongodb.client.model.UpdateOptions.class));
        return plan.getValue();
    }
}
