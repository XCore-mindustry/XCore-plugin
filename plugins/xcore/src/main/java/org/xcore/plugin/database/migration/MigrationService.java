package org.xcore.plugin.database.migration;

import arc.util.Log;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.network.RedisConnectionManager;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Singleton
public class MigrationService {
    public static final String REDIS_LOCK_KEY = "xcore:lock:migration";
    public static final long LOCK_EXPIRE_SECONDS = 60L;
    public static final long WAIT_TIMEOUT_MS = 60_000L;
    public static final long WAIT_POLL_INTERVAL_MS = 1_000L;

    private static final String LUA_RELEASE_LOCK =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private final MongoDatabase database;
    private final TomlSecretsConfig secretsConfig;
    private final TomlXcoreConfig config;
    @Nullable
    private final RedisConnectionManager redisConnectionManager;
    private final List<Migration> migrations;
    private long waitPollIntervalMs = WAIT_POLL_INTERVAL_MS;
    private long waitTimeoutMs = WAIT_TIMEOUT_MS;

    @Inject
    public MigrationService(MongoDatabase database,
                            TomlSecretsConfig secretsConfig,
                            TomlXcoreConfig config,
                            @Nullable RedisConnectionManager redisConnectionManager,
                            List<Migration> migrations) {
        this.database = database;
        this.secretsConfig = secretsConfig;
        this.config = config;
        this.redisConnectionManager = redisConnectionManager;
        this.migrations = migrations.stream()
                .sorted(Comparator.comparingInt(Migration::getVersion))
                .toList();
    }

    public MigrationService(MongoDatabase database,
                            TomlSecretsConfig secretsConfig,
                            TomlXcoreConfig config,
                            List<Migration> migrations) {
        this(database, secretsConfig, config, null, migrations);
    }

    void setWaitTimingForTesting(long waitTimeoutMs, long waitPollIntervalMs) {
        this.waitTimeoutMs = waitTimeoutMs;
        this.waitPollIntervalMs = waitPollIntervalMs;
    }

    public boolean run() {
        if (secretsConfig.database.readOnly) {
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

        if (!secretsConfig.database.migrationEnabled) {
            Log.warn("[Migrations] Update needed (v@ -> v@) but migrations are disabled.", dbVersion, targetVersion);
            secretsConfig.database.readOnly = true;
            return true;
        }

        RedisCommands<String, String> redisCommands = null;
        if (redisConnectionManager != null) {
            try {
                if (redisConnectionManager.ensureConnected()) {
                    redisCommands = redisConnectionManager.commands();
                }
            } catch (Exception e) {
                Log.warn("[Migrations] Redis unavailable for migration lock: @. Falling back to MongoDB lock.", e.getMessage());
            }
        }

        return runLockedMigrations(settings, dbVersion, targetVersion, redisCommands);
    }

    private boolean runLockedMigrations(MongoCollection<Document> settings,
                                        int dbVersion,
                                        int targetVersion,
                                        @Nullable RedisCommands<String, String> redisCommands) {
        if (redisCommands != null) {
            String token = config.server.name + ":" + UUID.randomUUID();
            if (tryRedisLock(redisCommands, token)) {
                try {
                    Log.info("[Migrations] Redis distributed lock acquired (token: @). Starting migrations...", token);
                    return executeMigrations(settings, dbVersion);
                } finally {
                    releaseRedisLock(redisCommands, token);
                }
            } else {
                String lockOwner = null;
                try {
                    lockOwner = redisCommands.get(REDIS_LOCK_KEY);
                } catch (Exception ignored) {
                }
                Log.warn("[Migrations] Another server (@) currently holds the Redis migration lock.",
                        lockOwner != null ? lockOwner : "unknown");
                return waitForMigrationsAndSync(settings, targetVersion, redisCommands);
            }
        }

        // Fallback to MongoDB lock
        if (tryMongoLock(settings)) {
            try {
                Log.info("[Migrations] MongoDB advisory lock acquired. Starting migrations...");
                return executeMigrations(settings, dbVersion);
            } finally {
                releaseMongoLock(settings);
            }
        } else {
            Log.warn("[Migrations] Another server (@) is currently performing migrations (MongoDB lock).",
                    getMongoLockOwner(settings));
            return waitForMigrationsAndSync(settings, targetVersion, null);
        }
    }

    private boolean tryRedisLock(RedisCommands<String, String> commands, String token) {
        try {
            String result = commands.set(REDIS_LOCK_KEY, token, SetArgs.Builder.nx().ex(LOCK_EXPIRE_SECONDS));
            return "OK".equalsIgnoreCase(result);
        } catch (Exception e) {
            Log.warn("[Migrations] Error trying to acquire Redis lock: @", e.getMessage());
            return false;
        }
    }

    private void releaseRedisLock(RedisCommands<String, String> commands, String token) {
        try {
            commands.eval(LUA_RELEASE_LOCK, ScriptOutputType.INTEGER, new String[]{REDIS_LOCK_KEY}, token);
            Log.info("[Migrations] Redis distributed lock released.");
        } catch (Exception e) {
            Log.warn("[Migrations] Failed to release Redis lock: @", e.getMessage());
        }
    }

    private boolean waitForMigrationsAndSync(MongoCollection<Document> settings,
                                             int targetVersion,
                                             @Nullable RedisCommands<String, String> redisCommands) {
        long deadline = System.currentTimeMillis() + waitTimeoutMs;
        Log.info("[Migrations] Waiting up to @s for active migration to finish...", waitTimeoutMs / 1000);

        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(waitPollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.warn("[Migrations] Interrupted while waiting for migrations.");
                return false;
            }

            var doc = settings.find(Filters.eq("_id", "db_version")).first();
            int currentVersion = (doc != null) ? doc.getInteger("version", 0) : 0;
            if (currentVersion >= targetVersion) {
                Log.info("[Migrations] Migrations completed by another instance. Database is now at v@.", currentVersion);
                return true;
            }

            // Check if lock was released or expired
            boolean lockFree = false;
            if (redisCommands != null) {
                try {
                    lockFree = (redisCommands.get(REDIS_LOCK_KEY) == null);
                } catch (Exception ignored) {
                }
            } else {
                lockFree = !isMongoLocked(settings);
            }

            if (lockFree) {
                Log.info("[Migrations] Migration lock was released. Checking version or retrying...");
                doc = settings.find(Filters.eq("_id", "db_version")).first();
                currentVersion = (doc != null) ? doc.getInteger("version", 0) : 0;
                if (currentVersion >= targetVersion) {
                    Log.info("[Migrations] Database is up to date (v@).", currentVersion);
                    return true;
                }
                // Try to acquire the lock ourselves to finish remaining migrations
                return runLockedMigrations(settings, currentVersion, targetVersion, redisCommands);
            }
        }

        Log.err("[Migrations] Timed out waiting for other instance to complete migrations (waited @s).", waitTimeoutMs / 1000);
        Log.warn("[Migrations] This server (@) will enter Read-Only mode until restart.", config.server.name);
        secretsConfig.database.readOnly = true;
        return true;
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

    private boolean tryMongoLock(MongoCollection<Document> settings) {
        long now = System.currentTimeMillis();
        try {
            settings.updateOne(
                    Filters.eq("_id", "migration_lock"),
                    Updates.setOnInsert("locked", false),
                    new UpdateOptions().upsert(true)
            );
        } catch (Exception ignored) {
        }

        var result = settings.findOneAndUpdate(
                Filters.and(
                        Filters.eq("_id", "migration_lock"),
                        Filters.or(
                                Filters.eq("locked", false),
                                Filters.lt("locked_at", now - 600_000L)
                        )
                ),
                Updates.combine(
                        Updates.set("locked", true),
                        Updates.set("locked_by", config.server.name),
                        Updates.set("locked_at", now)
                ),
                new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
        );

        return result != null && config.server.name.equals(result.getString("locked_by"));
    }

    private void releaseMongoLock(MongoCollection<Document> settings) {
        try {
            settings.updateOne(
                    Filters.eq("_id", "migration_lock"),
                    Updates.combine(
                            Updates.set("locked", false),
                            Updates.set("locked_by", ""),
                            Updates.set("locked_at", 0L)
                    )
            );
            Log.info("[Migrations] MongoDB lock released.");
        } catch (Exception e) {
            Log.warn("[Migrations] Error releasing MongoDB lock: @", e.getMessage());
        }
    }

    private boolean isMongoLocked(MongoCollection<Document> settings) {
        try {
            var doc = settings.find(Filters.eq("_id", "migration_lock")).first();
            if (doc == null) return false;
            boolean locked = Boolean.TRUE.equals(doc.getBoolean("locked", false));
            long lockedAt = doc.getLong("locked_at") != null ? doc.getLong("locked_at") : 0L;
            return locked && (System.currentTimeMillis() - lockedAt < 600_000L);
        } catch (Exception e) {
            return false;
        }
    }

    private String getMongoLockOwner(MongoCollection<Document> settings) {
        try {
            var doc = settings.find(Filters.eq("_id", "migration_lock")).first();
            return (doc != null) ? doc.getString("locked_by") : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
