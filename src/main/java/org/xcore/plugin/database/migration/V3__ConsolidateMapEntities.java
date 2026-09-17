package org.xcore.plugin.database.migration;

import arc.util.Log;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.ReplaceOptions;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.xcore.plugin.map.domain.MapDataConsolidator;
import org.xcore.plugin.map.domain.MapEntity;
import org.xcore.plugin.map.domain.MapSlug;
import org.xcore.plugin.model.MapData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

/**
 * Migration v3: Consolidates mode-split legacy {@link MapData} documents from the {@code "maps"}
 * collection into canonical {@link MapEntity} documents in the {@code "map_entities"} collection.
 */
@Singleton
public class V3__ConsolidateMapEntities implements Migration {

    private final MapDataConsolidator consolidator = new MapDataConsolidator();

    @Override
    public int getVersion() {
        return 3;
    }

    @Override
    public String getDescription() {
        return "Consolidate mode-split legacy MapData records into unified MapEntity documents";
    }

    @Override
    public void up(MongoDatabase database) {
        MongoCollection<MapData> mapsCol = database.getCollection("maps", MapData.class);
        MongoCollection<MapEntity> entitiesCol = database.getCollection("map_entities", MapEntity.class);

        // 1. Ensure target indexes
        entitiesCol.createIndex(
                new Document("content_hash", 1),
                new IndexOptions().sparse(true)
        );
        entitiesCol.createIndex(new Document("slug", 1));
        entitiesCol.createIndex(new Document("popularity", -1));
        entitiesCol.createIndex(new Document("reputation", -1));

        // 2. Load all legacy records
        List<MapData> legacyRecords = mapsCol.find().into(new ArrayList<>());
        if (legacyRecords.isEmpty()) {
            Log.info("[Migrations v3] No legacy map records found to consolidate.");
            return;
        }

        // 3. Group by physical contentHash (first priority) or canonical slug
        Map<String, List<MapData>> groups = new LinkedHashMap<>();
        for (MapData record : legacyRecords) {
            String groupKey;
            if (record.contentHash != null && !record.contentHash.isBlank()) {
                groupKey = "hash:" + record.contentHash;
            } else {
                groupKey = "slug:" + MapSlug.of(record.author, record.name).asSlug();
            }
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(record);
        }

        // 4. Consolidate and atomically upsert each group into "map_entities"
        int consolidatedCount = 0;
        ReplaceOptions upsert = new ReplaceOptions().upsert(true);
        for (List<MapData> group : groups.values()) {
            MapEntity entity = consolidator.consolidate(group);
            if (entity == null) continue;

            Bson filter = (entity.contentHash != null && !entity.contentHash.isBlank())
                    ? eq("content_hash", entity.contentHash)
                    : eq("slug", entity.slug);

            entitiesCol.replaceOne(filter, entity, upsert);
            consolidatedCount++;
        }

        Log.info("[Migrations v3] Successfully consolidated @ legacy map records into @ MapEntity documents.",
                legacyRecords.size(), consolidatedCount);
    }
}
