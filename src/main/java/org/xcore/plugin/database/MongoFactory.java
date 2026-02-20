package org.xcore.plugin.database;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import io.avaje.inject.PreDestroy;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.GlobalConfig;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;
import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;

@Factory
public class MongoFactory {

    private final GlobalConfig globalConfig;
    private MongoClient mongoClient;

    public MongoFactory(GlobalConfig globalConfig) {
        this.globalConfig = globalConfig;
    }

    @Bean
    public MongoClient mongoClient() {
        var connectionString = new ConnectionString(globalConfig.mongoConnectionString);
        var settings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();

        mongoClient = MongoClients.create(settings);
        return mongoClient;
    }

    @Bean
    public MongoDatabase mongoDatabase(MongoClient client) {
        CodecRegistry pojoCodecRegistry = fromRegistries(
                getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build())
        );

        PLog.info("MongoDB: using database '@'", globalConfig.databaseName);
        return client.getDatabase(globalConfig.databaseName)
                .withCodecRegistry(pojoCodecRegistry);
    }

    @PreDestroy
    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
