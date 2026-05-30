package org.xcore.plugin.config;

import arc.files.Fi;
import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigTomlLoaderTest {

    @TempDir
    Path tempDir;

    private final Gson gson = new SerializationFactory().prettyGson();

    @AfterEach
    void resetBackupTimestampSupplier() {
        ConfigTomlLoader.resetBackupTimestampSupplier();
    }

    @Test
    @DisplayName("loadXcoreConfig returns TOML source when xcore.toml exists")
    void loadXcoreConfig_returnsTomlSource_whenTomlExists() throws IOException {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Files.writeString(tomlPath, """
                version = 1

                [server]
                name = "test-server"
                player_limit = 42
                """);

        Fi dataDir = new Fi(tempDir.toFile());
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(dataDir, gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.file.name()).isEqualTo("xcore.toml");
        assertThat(result.config.server.name).isEqualTo("test-server");
        assertThat(result.config.server.playerLimit).isEqualTo(42);
    }

    @Test
    @DisplayName("loadXcoreConfig returns LEGACY_JSON source when only xcconfig.json exists")
    void loadXcoreConfig_returnsLegacyJsonSource_whenOnlyJsonExists() throws IOException {
        Path jsonPath = tempDir.resolve("xcconfig.json");
        Files.writeString(jsonPath, """
                {
                  "server": "legacy-server",
                  "player_limit": 99
                }
                """);

        ConfigTomlLoader.setBackupTimestampSupplier(() -> LocalDateTime.of(2026, 5, 24, 16, 30, 45));

        Fi dataDir = new Fi(tempDir.toFile());
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(dataDir, gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.MIGRATED);
        assertThat(result.file.name()).isEqualTo("xcore.toml");
        assertThat(result.backupFile).isNotNull();
        assertThat(result.backupFile.name()).isEqualTo("xcconfig.json.bak-20260524-163045");
        assertThat(tempDir.resolve("xcconfig.json")).doesNotExist();
        assertThat(tempDir.resolve("xcore.toml")).exists();
        assertThat(tempDir.resolve("xcconfig.json.bak-20260524-163045")).exists();
        assertThat(Files.readString(tempDir.resolve("xcore.toml")))
                .contains("[server]")
                .contains("name = \"legacy-server\"")
                .doesNotContain("server.name =");
        assertThat(result.config.server.name).isEqualTo("legacy-server");
        assertThat(result.config.server.playerLimit).isEqualTo(99);
    }

    @Test
    @DisplayName("loadXcoreConfig returns DEFAULT_TEMPLATE source and creates file when neither exists")
    void loadXcoreConfig_returnsDefaultTemplateSource_whenNeitherExists() {
        Fi dataDir = new Fi(tempDir.toFile());
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(dataDir, gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.DEFAULT_TEMPLATE);
        assertThat(result.file.name()).isEqualTo("xcore.toml");
        assertThat(result.file.exists()).isTrue();
        assertThat(result.config.server.name).isEqualTo("server");
        assertThat(result.config.server.playerLimit).isEqualTo(30);
    }

    @Test
    @DisplayName("loadXcoreConfig prefers TOML over legacy JSON when both exist")
    void loadXcoreConfig_prefersTomlOverLegacyJson() throws IOException {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Files.writeString(tomlPath, """
                version = 1

                [server]
                name = "toml-wins"
                """);

        Path jsonPath = tempDir.resolve("xcconfig.json");
        Files.writeString(jsonPath, """
                {
                  "server": "json-loses"
                }
                """);

        Fi dataDir = new Fi(tempDir.toFile());
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(dataDir, gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.config.server.name).isEqualTo("toml-wins");
    }

    @Test
    @DisplayName("loadGlobalConfig returns TOML source when secrets.toml exists")
    void loadGlobalConfig_returnsTomlSource_whenTomlExists() throws IOException {
        Path tomlPath = tempDir.resolve("secrets.toml");
        Files.writeString(tomlPath, """
                version = 1

                [database]
                mongo_connection_string = "mongodb://toml:27017"
                name = "toml-db"
                """);

        ConfigTomlLoader.LoadResult<GlobalConfig> result = ConfigTomlLoader.loadGlobalConfig(tempDir.toString(), gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.file.name()).isEqualTo("secrets.toml");
        assertThat(result.config.mongoConnectionString).isEqualTo("mongodb://toml:27017");
        assertThat(result.config.databaseName).isEqualTo("toml-db");
    }

    @Test
    @DisplayName("loadTomlSecretsConfig returns TOML source when secrets.toml exists")
    void loadTomlSecretsConfig_returnsTomlSource_whenTomlExists() throws IOException {
        Path tomlPath = tempDir.resolve("secrets.toml");
        Files.writeString(tomlPath, """
                version = 1

                [database]
                mongo_connection_string = "mongodb://toml:27017"
                name = "toml-db"
                """);

        ConfigTomlLoader.LoadResult<TomlSecretsConfig> result = ConfigTomlLoader.loadTomlSecretsConfig(tempDir.toString(), gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.file.name()).isEqualTo("secrets.toml");
        assertThat(result.config.database.mongoConnectionString).isEqualTo("mongodb://toml:27017");
        assertThat(result.config.database.name).isEqualTo("toml-db");
    }

    @Test
    @DisplayName("loadGlobalConfig returns LEGACY_JSON source when only secrets.json exists")
    void loadGlobalConfig_returnsLegacyJsonSource_whenOnlyJsonExists() throws IOException {
        Path jsonPath = tempDir.resolve("secrets.json");
        Files.writeString(jsonPath, """
                {
                  "mongo_connection_string": "mongodb://json:27017",
                  "database_name": "json-db"
                }
                """);

        ConfigTomlLoader.setBackupTimestampSupplier(() -> LocalDateTime.of(2026, 5, 24, 16, 31, 46));

        ConfigTomlLoader.LoadResult<GlobalConfig> result = ConfigTomlLoader.loadGlobalConfig(tempDir.toString(), gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.MIGRATED);
        assertThat(result.file.name()).isEqualTo("secrets.toml");
        assertThat(result.backupFile).isNotNull();
        assertThat(result.backupFile.name()).isEqualTo("secrets.json.bak-20260524-163146");
        assertThat(tempDir.resolve("secrets.json")).doesNotExist();
        assertThat(tempDir.resolve("secrets.toml")).exists();
        assertThat(tempDir.resolve("secrets.json.bak-20260524-163146")).exists();
        assertThat(Files.readString(tempDir.resolve("secrets.toml")))
                .contains("[database]")
                .contains("mongo_connection_string = \"mongodb://json:27017\"")
                .doesNotContain("database.mongo_connection_string =");
        assertThat(result.config.mongoConnectionString).isEqualTo("mongodb://json:27017");
        assertThat(result.config.databaseName).isEqualTo("json-db");
    }

    @Test
    @DisplayName("loadTomlSecretsConfig returns MIGRATED source when only secrets.json exists")
    void loadTomlSecretsConfig_returnsMigratedSource_whenOnlyJsonExists() throws IOException {
        Path jsonPath = tempDir.resolve("secrets.json");
        Files.writeString(jsonPath, """
                {
                  "mongo_connection_string": "mongodb://json:27017",
                  "database_name": "json-db"
                }
                """);

        ConfigTomlLoader.setBackupTimestampSupplier(() -> LocalDateTime.of(2026, 5, 24, 16, 31, 46));

        ConfigTomlLoader.LoadResult<TomlSecretsConfig> result = ConfigTomlLoader.loadTomlSecretsConfig(tempDir.toString(), gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.MIGRATED);
        assertThat(result.file.name()).isEqualTo("secrets.toml");
        assertThat(result.backupFile).isNotNull();
        assertThat(result.backupFile.name()).isEqualTo("secrets.json.bak-20260524-163146");
        assertThat(tempDir.resolve("secrets.json")).doesNotExist();
        assertThat(tempDir.resolve("secrets.toml")).exists();
        assertThat(tempDir.resolve("secrets.json.bak-20260524-163146")).exists();
        assertThat(Files.readString(tempDir.resolve("secrets.toml")))
                .contains("[database]")
                .contains("mongo_connection_string = \"mongodb://json:27017\"")
                .doesNotContain("database.mongo_connection_string =");
        assertThat(result.config.database.mongoConnectionString).isEqualTo("mongodb://json:27017");
        assertThat(result.config.database.name).isEqualTo("json-db");
    }

    @Test
    @DisplayName("loadGlobalConfig returns DEFAULT_TEMPLATE source and creates file when neither exists")
    void loadGlobalConfig_returnsDefaultTemplateSource_whenNeitherExists() {
        ConfigTomlLoader.LoadResult<GlobalConfig> result = ConfigTomlLoader.loadGlobalConfig(tempDir.toString(), gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.DEFAULT_TEMPLATE);
        assertThat(result.file.name()).isEqualTo("secrets.toml");
        assertThat(result.file.exists()).isTrue();
        assertThat(result.config.mongoConnectionString).isNull();
        assertThat(result.config.databaseName).isNull();
    }

    @Test
    @DisplayName("loadTomlSecretsConfig returns DEFAULT_TEMPLATE source and creates file when neither exists")
    void loadTomlSecretsConfig_returnsDefaultTemplateSource_whenNeitherExists() {
        ConfigTomlLoader.LoadResult<TomlSecretsConfig> result = ConfigTomlLoader.loadTomlSecretsConfig(tempDir.toString(), gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.DEFAULT_TEMPLATE);
        assertThat(result.file.name()).isEqualTo("secrets.toml");
        assertThat(result.file.exists()).isTrue();
        assertThat(result.config.database.mongoConnectionString).isEqualTo("");
        assertThat(result.config.database.name).isEqualTo("");
    }

    @Test
    @DisplayName("loadGlobalConfig prefers TOML over legacy JSON when both exist")
    void loadGlobalConfig_prefersTomlOverLegacyJson() throws IOException {
        Path tomlPath = tempDir.resolve("secrets.toml");
        Files.writeString(tomlPath, """
                version = 1

                [database]
                mongo_connection_string = "mongodb://toml:27017"
                name = "toml-db"
                """);

        Path jsonPath = tempDir.resolve("secrets.json");
        Files.writeString(jsonPath, """
                {
                  "mongo_connection_string": "mongodb://json:27017",
                  "database_name": "json-db"
                }
                """);

        ConfigTomlLoader.LoadResult<GlobalConfig> result = ConfigTomlLoader.loadGlobalConfig(tempDir.toString(), gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.config.mongoConnectionString).isEqualTo("mongodb://toml:27017");
        assertThat(result.config.databaseName).isEqualTo("toml-db");
    }

    @Test
    @DisplayName("loadTomlSecretsConfig prefers TOML over legacy JSON when both exist")
    void loadTomlSecretsConfig_prefersTomlOverLegacyJson() throws IOException {
        Path tomlPath = tempDir.resolve("secrets.toml");
        Files.writeString(tomlPath, """
                version = 1

                [database]
                mongo_connection_string = "mongodb://toml:27017"
                name = "toml-db"
                """);

        Path jsonPath = tempDir.resolve("secrets.json");
        Files.writeString(jsonPath, """
                {
                  "mongo_connection_string": "mongodb://json:27017",
                  "database_name": "json-db"
                }
                """);

        ConfigTomlLoader.LoadResult<TomlSecretsConfig> result = ConfigTomlLoader.loadTomlSecretsConfig(tempDir.toString(), gson);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.config.database.mongoConnectionString).isEqualTo("mongodb://toml:27017");
        assertThat(result.config.database.name).isEqualTo("toml-db");
    }

    @Test
    @DisplayName("loadXcoreConfig backup helper creates deterministic sibling backup name")
    void backupLegacyFile_createsDeterministicSiblingBackupName() throws IOException {
        Path jsonPath = tempDir.resolve("xcconfig.json");
        Files.writeString(jsonPath, "{}");
        ConfigTomlLoader.setBackupTimestampSupplier(() -> LocalDateTime.of(2026, 5, 24, 17, 1, 2));

        Fi backupFile = ConfigTomlLoader.backupLegacyFile(new Fi(jsonPath.toFile()));

        assertThat(jsonPath).doesNotExist();
        assertThat(backupFile.name()).isEqualTo("xcconfig.json.bak-20260524-170102");
        assertThat(tempDir.resolve("xcconfig.json.bak-20260524-170102")).exists();
    }
}
