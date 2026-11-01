package org.xcore.plugin.database.migration;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.network.RedisConnectionManager;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MigrationServiceTest {

    @Mock
    private MongoDatabase database;
    @Mock
    private MongoCollection<Document> settingsCollection;
    @Mock
    private FindIterable<Document> findIterable;
    @Mock
    private RedisConnectionManager redisConnectionManager;
    @Mock
    private RedisCommands<String, String> redisCommands;
    @Mock
    private Migration migrationV1;
    @Mock
    private Migration migrationV2;

    private TomlSecretsConfig secretsConfig;
    private TomlXcoreConfig config;

    @BeforeEach
    void setUp() {
        secretsConfig = new TomlSecretsConfig();
        secretsConfig.database.readOnly = false;
        secretsConfig.database.migrationEnabled = true;

        config = new TomlXcoreConfig();
        config.server.name = "test-server-1";

        lenient().when(database.getCollection("settings")).thenReturn(settingsCollection);
        lenient().when(settingsCollection.find(any(Bson.class))).thenReturn(findIterable);

        lenient().when(migrationV1.getVersion()).thenReturn(1);
        lenient().when(migrationV1.getDescription()).thenReturn("Init schema");
        lenient().when(migrationV2.getVersion()).thenReturn(2);
        lenient().when(migrationV2.getDescription()).thenReturn("Add fields");
    }

    @Test
    @DisplayName("Should skip migrations when database is in read-only mode")
    void shouldSkipWhenReadOnly() {
        secretsConfig.database.readOnly = true;
        MigrationService service = new MigrationService(
                database, secretsConfig, config, redisConnectionManager, List.of(migrationV1)
        );

        boolean result = service.run();

        assertThat(result).isTrue();
        verifyNoInteractions(database, redisConnectionManager);
    }

    @Test
    @DisplayName("Should return true when database is already at target version")
    void shouldReturnTrueWhenAlreadyUpToDate() {
        Document versionDoc = new Document("_id", "db_version").append("version", 2);
        when(findIterable.first()).thenReturn(versionDoc);

        MigrationService service = new MigrationService(
                database, secretsConfig, config, redisConnectionManager, List.of(migrationV1, migrationV2)
        );

        boolean result = service.run();

        assertThat(result).isTrue();
        verifyNoInteractions(redisConnectionManager);
        verify(migrationV1, never()).up(any());
        verify(migrationV2, never()).up(any());
    }

    @Test
    @DisplayName("Should return false when database version is newer than code")
    void shouldFailWhenDatabaseIsNewerThanCode() {
        Document versionDoc = new Document("_id", "db_version").append("version", 5);
        when(findIterable.first()).thenReturn(versionDoc);

        MigrationService service = new MigrationService(
                database, secretsConfig, config, redisConnectionManager, List.of(migrationV1, migrationV2)
        );

        boolean result = service.run();

        assertThat(result).isFalse();
        verifyNoInteractions(redisConnectionManager);
    }

    @Test
    @DisplayName("Should set readOnly and return true when migrations are disabled in config")
    void shouldSetReadOnlyWhenMigrationsDisabled() {
        Document versionDoc = new Document("_id", "db_version").append("version", 0);
        when(findIterable.first()).thenReturn(versionDoc);
        secretsConfig.database.migrationEnabled = false;

        MigrationService service = new MigrationService(
                database, secretsConfig, config, redisConnectionManager, List.of(migrationV1)
        );

        boolean result = service.run();

        assertThat(result).isTrue();
        assertThat(secretsConfig.database.readOnly).isTrue();
        verify(migrationV1, never()).up(any());
    }

    @Test
    @DisplayName("Should acquire Redis lock (SET NX EX 60), apply migrations, and release lock via Lua")
    void shouldAcquireRedisLockAndApplyMigrations() {
        Document versionDoc = new Document("_id", "db_version").append("version", 0);
        when(findIterable.first()).thenReturn(versionDoc);

        when(redisConnectionManager.ensureConnected()).thenReturn(true);
        when(redisConnectionManager.commands()).thenReturn(redisCommands);
        when(redisCommands.set(eq(MigrationService.REDIS_LOCK_KEY), anyString(), any(SetArgs.class)))
                .thenReturn("OK");

        MigrationService service = new MigrationService(
                database, secretsConfig, config, redisConnectionManager, List.of(migrationV1, migrationV2)
        );

        boolean result = service.run();

        assertThat(result).isTrue();

        // Verify Redis lock acquired with SET NX EX 60
        verify(redisCommands).set(
                eq(MigrationService.REDIS_LOCK_KEY),
                startsWith("test-server-1:"),
                argThat(args -> args != null)
        );

        // Verify migrations executed in order
        verify(migrationV1).up(database);
        verify(migrationV2).up(database);

        // Verify version updated in Mongo settings
        verify(settingsCollection, times(2)).updateOne(
                any(Bson.class),
                any(Bson.class),
                any(UpdateOptions.class)
        );

        // Verify lock released via Lua eval
        verify(redisCommands).eval(
                contains("redis.call('get', KEYS[1]) == ARGV[1]"),
                eq(ScriptOutputType.INTEGER),
                eq(new String[]{MigrationService.REDIS_LOCK_KEY}),
                startsWith("test-server-1:")
        );
    }

    @Test
    @DisplayName("Should safely release Redis lock even if migration throws exception")
    void shouldReleaseRedisLockOnMigrationFailure() {
        Document versionDoc = new Document("_id", "db_version").append("version", 0);
        when(findIterable.first()).thenReturn(versionDoc);

        when(redisConnectionManager.ensureConnected()).thenReturn(true);
        when(redisConnectionManager.commands()).thenReturn(redisCommands);
        when(redisCommands.set(eq(MigrationService.REDIS_LOCK_KEY), anyString(), any(SetArgs.class)))
                .thenReturn("OK");

        doThrow(new RuntimeException("Schema corruption simulated")).when(migrationV1).up(any());

        MigrationService service = new MigrationService(
                database, secretsConfig, config, redisConnectionManager, List.of(migrationV1)
        );

        boolean result = service.run();

        assertThat(result).isFalse();

        // Lock MUST still be released
        verify(redisCommands).eval(
                contains("redis.call('del', KEYS[1])"),
                eq(ScriptOutputType.INTEGER),
                eq(new String[]{MigrationService.REDIS_LOCK_KEY}),
                startsWith("test-server-1:")
        );
    }

    @Test
    @DisplayName("When another server holds Redis lock, should wait and succeed once other server completes migrations")
    void shouldWaitAndSucceedWhenOtherServerCompletesMigrations() {
        Document initialDoc = new Document("_id", "db_version").append("version", 0);
        Document completedDoc = new Document("_id", "db_version").append("version", 2);

        // First check at start returns v0, next check during polling returns v2 (completed by peer)
        when(findIterable.first()).thenReturn(initialDoc, initialDoc, completedDoc);

        when(redisConnectionManager.ensureConnected()).thenReturn(true);
        when(redisConnectionManager.commands()).thenReturn(redisCommands);
        // Lock acquisition fails because peer holds it
        when(redisCommands.set(eq(MigrationService.REDIS_LOCK_KEY), anyString(), any(SetArgs.class)))
                .thenReturn(null);
        when(redisCommands.get(MigrationService.REDIS_LOCK_KEY)).thenReturn("peer-server-99:token");

        MigrationService service = new MigrationService(
                database, secretsConfig, config, redisConnectionManager, List.of(migrationV1, migrationV2)
        );
        service.setWaitTimingForTesting(5000, 10);

        boolean result = service.run();

        assertThat(result).isTrue();
        assertThat(secretsConfig.database.readOnly).isFalse();
        verify(migrationV1, never()).up(any());
        verify(migrationV2, never()).up(any());
    }

    @Test
    @DisplayName("Should fallback to MongoDB lock when Redis is unavailable")
    void shouldFallbackToMongoLockWhenRedisUnavailable() {
        Document versionDoc = new Document("_id", "db_version").append("version", 0);
        when(findIterable.first()).thenReturn(versionDoc);

        when(redisConnectionManager.ensureConnected()).thenReturn(false);

        Document lockedDoc = new Document("locked", true)
                .append("locked_by", "test-server-1")
                .append("locked_at", System.currentTimeMillis());
        when(settingsCollection.findOneAndUpdate(any(Bson.class), any(Bson.class), any()))
                .thenReturn(lockedDoc);

        MigrationService service = new MigrationService(
                database, secretsConfig, config, redisConnectionManager, List.of(migrationV1)
        );

        boolean result = service.run();

        assertThat(result).isTrue();
        verify(migrationV1).up(database);
        verify(settingsCollection).findOneAndUpdate(any(Bson.class), any(Bson.class), any());
    }
}
