package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.ReactiveMongoStore;

@Singleton
public class AdminDataRepository extends PlayerDataRepository {

    @Inject
    public AdminDataRepository(MongoDatabase database, ReactiveMongoStore reactiveMongoStore, TomlSecretsConfig secretsConfig) {
        super(database, reactiveMongoStore, secretsConfig);
    }
}
