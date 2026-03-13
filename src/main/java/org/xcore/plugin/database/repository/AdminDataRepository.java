package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.PlayerData;

@Singleton
public class AdminDataRepository extends PlayerDataRepository {

    @Inject
    public AdminDataRepository(MongoDatabase database, GlobalConfig globalConfig) {
        super(database, globalConfig);
    }

    public void delete(String uuid) {
        PlayerData playerData = findByUuid(uuid);
        if (playerData != null) {
            playerData.admin = false;
            playerData.adminSource = DiscordAdminSource.NONE.value();
            save(playerData);
        }
    }

    private enum DiscordAdminSource {
        NONE("NONE");

        private final String value;

        DiscordAdminSource(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
