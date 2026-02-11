package org.xcore.plugin.database.migration;

import arc.util.Log;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.config.GlobalConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Singleton
public class MigrationService {
    private final MongoDatabase database;
    private final GlobalConfig globalConfig;
    private final List<Migration> migrations = new ArrayList<>();

    @Inject
    public MigrationService(MongoDatabase database, GlobalConfig globalConfig) {
        this.database = database;
        this.globalConfig = globalConfig;

        migrations.add(new V1__InitModernDatabase());

        migrations.sort(Comparator.comparingInt(Migration::getVersion));
    }

    public boolean run() {
        if (globalConfig.isDataBaseReadOnly) {
            Log.info("[Migrations] Database is in Read-Only mode. Skipping migration check.");
            return true;
        }

        var settings = database.getCollection("settings");
        var doc = settings.find(new Document("_id", "db_version")).first();
        int dbVersion = (doc != null) ? doc.getInteger("version", 0) : 0;

        int targetVersion = migrations.stream()
                .mapToInt(Migration::getVersion)
                .max()
                .orElse(0);


        if (dbVersion == targetVersion) {
            Log.info("[Migrations] Database version matches code (v@).", dbVersion);
            return true;
        }

        if (dbVersion > targetVersion) {
            Log.err("[Migrations] CRITICAL: Database version (v@) is newer than supported by this plugin (v@)!",
                    dbVersion, targetVersion);
            Log.err("[Migrations] Please update the plugin or check your database.");
            return false;
        }

        Log.warn("[Migrations] Database version (v@) is older than code (v@).", dbVersion, targetVersion);

        if (!globalConfig.isDataBaseMigration) {
            Log.err("[Migrations] Migration is required but 'isDataBaseMigration' is set to false!");

            Log.warn("[Migrations] Switching to EMERGENCY Read-Only mode to prevent data corruption.");
            globalConfig.isDataBaseReadOnly = true;
            return true;
        }

        Log.info("[Migrations] Starting migration process...");

        for (Migration m : migrations) {
            if (m.getVersion() > dbVersion) {
                Log.info("[Migrations] Applying v@: @...", m.getVersion(), m.getDescription());

                try {
                    m.up(database);

                    dbVersion = m.getVersion();
                    settings.updateOne(
                        new Document("_id", "db_version"),
                        new Document("$set", new Document("version", dbVersion)),
                        new UpdateOptions().upsert(true)
                    );

                    Log.info("[Migrations] v@ applied successfully.", dbVersion);
                } catch (Exception e) {
                    Log.err("[Migrations] CRITICAL ERROR during migration v@: @", m.getVersion(), e.getMessage());
                    Log.err(e);
                    return false;
                }
            }
        }

        Log.info("[Migrations] All migrations completed. Current version: v@", dbVersion);
        return true;
    }
}