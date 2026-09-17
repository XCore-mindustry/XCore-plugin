package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.MongoAsync;
import org.xcore.plugin.database.ReactiveMongoStore;
import org.xcore.plugin.map.domain.MapEntity;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static com.mongodb.client.model.Filters.eq;

/**
 * Repository for persistent {@link MapEntity} records in MongoDB collection {@code "map_entities"}.
 * Backed by reactive streams for non-blocking UI and telemetry reads.
 */
@Singleton
public class MapEntityRepository extends DataRepository<MapEntity> {

    @Inject
    public MapEntityRepository(MongoDatabase database,
                               ReactiveMongoStore reactiveMongoStore,
                               TomlSecretsConfig secretsConfig) {
        super(database, reactiveMongoStore, "map_entities", MapEntity.class, secretsConfig);

        collection.createIndex(
                new Document("content_hash", 1),
                new IndexOptions().unique(true).sparse(true)
        );
        collection.createIndex(new Document("slug", 1));
        collection.createIndex(new Document("popularity", -1));
        collection.createIndex(new Document("reputation", -1));
    }

    public MapEntityRepository(MongoDatabase database, TomlSecretsConfig secretsConfig) {
        this(database, null, secretsConfig);
    }

    public CompletionStage<MapEntity> findByContentHashAsync(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        if (reactiveCollection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for findByContentHashAsync"));
        }
        return MongoAsync.first(reactiveCollection.find(eq("content_hash", contentHash)));
    }

    public CompletionStage<MapEntity> findBySlugAsync(String slug) {
        if (slug == null || slug.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        if (reactiveCollection == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Reactive MongoDB store is required for findBySlugAsync"));
        }
        return MongoAsync.first(reactiveCollection.find(eq("slug", slug)));
    }

    public Optional<MapEntity> findByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(collection.find(eq("content_hash", contentHash)).first());
    }

    public Optional<MapEntity> findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(collection.find(eq("slug", slug)).first());
    }
}
