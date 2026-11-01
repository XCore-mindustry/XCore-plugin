package org.xcore.plugin.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.xcore.plugin.config.TomlSecretsConfig;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;

/** Owns the native Reactive Streams MongoDB client used by async repositories. */
@Singleton
public class ReactiveMongoStore {
    private final com.mongodb.reactivestreams.client.MongoClient client;
    private final com.mongodb.reactivestreams.client.MongoDatabase database;

    @Inject
    public ReactiveMongoStore(TomlSecretsConfig config) {
        var connectionString = new ConnectionString(config.database.mongoConnectionString);
        var settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();

        this.client = com.mongodb.reactivestreams.client.MongoClients.create(settings);
        this.database = client.getDatabase(config.database.name)
                .withCodecRegistry(pojoCodecRegistry());
    }

    private CodecRegistry pojoCodecRegistry() {
        return fromRegistries(
                getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );
    }

    public <T> com.mongodb.reactivestreams.client.MongoCollection<T> collection(
            String name,
            Class<T> documentClass) {
        return database.getCollection(name, documentClass);
    }

    @PreDestroy
    public void close() {
        client.close();
    }
}
