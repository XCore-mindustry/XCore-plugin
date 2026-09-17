package org.xcore.plugin.database.migration;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.map.domain.MapEntity;
import org.xcore.plugin.model.MapData;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class V3__ConsolidateMapEntitiesTest {

    @Test
    @DisplayName("Metadata identifies migration as version 3 with accurate description")
    void metadataMatchesVersionThree() {
        V3__ConsolidateMapEntities migration = new V3__ConsolidateMapEntities();
        assertThat(migration.getVersion()).isEqualTo(3);
        assertThat(migration.getDescription()).containsIgnoringCase("consolidate");
    }

    @Test
    @DisplayName("up groups legacy MapData by hash and slug, inserts MapEntity, and creates indexes")
    @SuppressWarnings("unchecked")
    void upConsolidatesLegacyRecordsIntoMapEntities() {
        MongoDatabase database = mock(MongoDatabase.class);
        MongoCollection<MapData> mapsCol = mock(MongoCollection.class);
        MongoCollection<MapEntity> entitiesCol = mock(MongoCollection.class);

        when(database.getCollection(eq("maps"), eq(MapData.class))).thenReturn(mapsCol);
        when(database.getCollection(eq("map_entities"), eq(MapEntity.class))).thenReturn(entitiesCol);

        FindIterable<MapData> findIterable = mock(FindIterable.class);
        when(mapsCol.find()).thenReturn(findIterable);

        var record1 = new MapData("Arena", "arena.msav", "Author", "survival");
        record1.contentHash = "hash1";
        record1.like = 5;
        record1.playedTimes = 20;

        var record2 = new MapData("Arena", "arena.msav", "Author", "pvp");
        record2.contentHash = "hash1";
        record2.like = 3;
        record2.playedTimes = 10;

        var record3 = new MapData("Desert", "desert.msav", "Anuke", "attack");
        record3.like = 7;
        record3.playedTimes = 15;

        List<MapData> legacyList = List.of(record1, record2, record3);
        when(findIterable.into(any())).thenAnswer(inv -> {
            java.util.Collection<MapData> target = inv.getArgument(0);
            target.addAll(legacyList);
            return target;
        });

        V3__ConsolidateMapEntities migration = new V3__ConsolidateMapEntities();
        migration.up(database);

        // Verify index creation
        verify(entitiesCol).createIndex(eq(new Document("content_hash", 1)), any());
        verify(entitiesCol).createIndex(eq(new Document("slug", 1)));

        // Verify entities consolidated and saved
        ArgumentCaptor<MapEntity> entityCaptor = ArgumentCaptor.forClass(MapEntity.class);
        verify(entitiesCol, times(2)).replaceOne(any(Bson.class), entityCaptor.capture(), any(ReplaceOptions.class));

        List<MapEntity> saved = entityCaptor.getAllValues();
        assertThat(saved).hasSize(2);

        MapEntity arenaEntity = saved.stream().filter(e -> "hash1".equals(e.contentHash)).findFirst().orElseThrow();
        assertThat(arenaEntity.likes).isEqualTo(8);
        assertThat(arenaEntity.totalPlayCount).isEqualTo(30);
        assertThat(arenaEntity.gamemodeStats).containsKeys("survival", "pvp");

        MapEntity desertEntity = saved.stream().filter(e -> "anuke/desert".equals(e.slug)).findFirst().orElseThrow();
        assertThat(desertEntity.likes).isEqualTo(7);
        assertThat(desertEntity.totalPlayCount).isEqualTo(15);
    }
}
