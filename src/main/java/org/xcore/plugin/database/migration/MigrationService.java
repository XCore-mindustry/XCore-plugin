package org.xcore.plugin.database.migration;

import arc.util.Log;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.UpdateOptions;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.config.Config; // Твій звичайний конфіг з назвою сервера
import org.xcore.plugin.config.GlobalConfig;

import java.util.Comparator;
import java.util.List;

@Singleton
public class MigrationService {
    private final MongoDatabase database;
    private final GlobalConfig globalConfig;
    private final Config config;
    private final List<Migration> migrations;

    public MigrationService(MongoDatabase database, GlobalConfig globalConfig, Config config, List<Migration> migrations) {
        this.database = database;
        this.globalConfig = globalConfig;
        this.config = config;
        this.migrations = migrations.stream()
                .sorted(Comparator.comparingInt(Migration::getVersion))
                .toList();
    }

    public boolean run() {
        if (globalConfig.isDataBaseReadOnly) {
            Log.info("[Migrations] Database is in Read-Only mode. Skipping.");
            return true;
        }

        var settings = database.getCollection("settings");

        var doc = settings.find(Filters.eq("_id", "db_version")).first();
        int dbVersion = (doc != null) ? doc.getInteger("version", 0) : 0;
        int targetVersion = migrations.stream().mapToInt(Migration::getVersion).max().orElse(0);

        if (dbVersion >= targetVersion) {
            if (dbVersion > targetVersion) {
                Log.err("[Migrations] Database version (v@) is newer than code (v@)!", dbVersion, targetVersion);
                return false;
            }
            Log.info("[Migrations] Database is up to date (v@).", dbVersion);
            return true;
        }

        if (!globalConfig.isDataBaseMigration) {
            Log.warn("[Migrations] Update needed (v@ -> v@) but migrations are disabled.", dbVersion, targetVersion);
            globalConfig.isDataBaseReadOnly = true;
            return true;
        }

        if (tryLock(settings)) {
            try {
                Log.info("[Migrations] Lock acquired. Starting migrations...");
                return executeMigrations(settings, dbVersion);
            } finally {
                releaseLock(settings);
            }
        } else {
            Log.warn("[Migrations] Another server (@) is currently performing migrations.", getLockOwner(settings));
            Log.warn("[Migrations] This server (@) will enter Read-Only mode until restart.", config.server);
            globalConfig.isDataBaseReadOnly = true;
            return true;
        }
    }

    private boolean executeMigrations(MongoCollection<Document> settings, int currentVersion) {
        int dbVersion = currentVersion;
        for (Migration m : migrations) {
            if (m.getVersion() > dbVersion) {
                Log.info("[Migrations] Applying v@: @...", m.getVersion(), m.getDescription());
                try {
                    m.up(database);
                    dbVersion = m.getVersion();
                    settings.updateOne(
                        Filters.eq("_id", "db_version"),
                        Updates.set("version", dbVersion),
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
        return true;
    }

    private boolean tryLock(MongoCollection<Document> settings) {
        long now = System.currentTimeMillis();
        var result = settings.findOneAndUpdate(
            Filters.and(
                Filters.eq("_id", "migration_lock"),
                Filters.or(
                    Filters.eq("locked", false),
                    Filters.lt("locked_at", now - 600000)
                )
            ),
            Updates.combine(
                Updates.set("locked", true),
                Updates.set("locked_by", config.server),
                Updates.set("locked_at", now)
            ),
            new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
        );

        return result != null && result.getString("locked_by").equals(config.server);
    }

    private void releaseLock(MongoCollection<Document> settings) {
        settings.updateOne(
            Filters.eq("_id", "migration_lock"),
            Updates.combine(
                Updates.set("locked", false),
                Updates.set("locked_by", ""),
                Updates.set("locked_at", 0L)
            )
        );
        Log.info("[Migrations] Lock released.");
    }

    private String getLockOwner(MongoCollection<Document> settings) {
        var doc = settings.find(Filters.eq("_id", "migration_lock")).first();
        return (doc != null) ? doc.getString("locked_by") : "unknown";
    }
}
