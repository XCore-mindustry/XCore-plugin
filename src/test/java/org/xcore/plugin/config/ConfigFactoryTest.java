package org.xcore.plugin.config;

import arc.files.Fi;
import com.google.gson.Gson;
import mindustry.Vars;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigFactoryTest {

    @TempDir
    static Path tempDir;

    private static Gson prettyGson;

    @BeforeAll
    static void setUpDataDirectory() {
        Vars.dataDirectory = new Fi(tempDir.toFile());
        prettyGson = new SerializationFactory().prettyGson();
    }

    @BeforeEach
    void cleanConfigFiles() throws IOException {
        Files.deleteIfExists(tempDir.resolve("xcconfig.json"));
        Files.deleteIfExists(tempDir.resolve("xcore.toml"));
        ConfigTomlLoader.resetBackupTimestampSupplier();
    }

    @Test
    @DisplayName("server-local config creates missing xcore.toml from defaults and projects normalized Config")
    void config_createsMissingXcoreTomlFromDefaultsAndLoadsNormalizedConfig() {
        // Arrange
        ConfigFactory factory = new ConfigFactory();

        // Act
        TomlXcoreConfig serverLocalConfig = factory.serverLocalConfig(prettyGson);
        Config config = factory.config(serverLocalConfig);

        // Assert
        assertThat(serverLocalConfig.server.name).isEqualTo("server");
        assertThat(config.server).isEqualTo("server");
        assertThat(config.disabledCommands).isNotNull().isEmpty();
        assertThat(config.disabledFeatures).isNotNull().isEmpty();
        assertThat(config.translation).isNotNull();
        assertThat(config.translation.pipeline).containsExactly("google");

        Path tomlPath = tempDir.resolve("xcore.toml");
        assertThat(tomlPath).exists();
        assertThat(read(tomlPath)).contains("name = \"server\"");
    }

    @Test
    @DisplayName("server-local config loads xcore.toml when both TOML and legacy JSON exist")
    void config_tomlTakesPrecedenceOverLegacyJson() throws IOException {
        // Arrange
        Path tomlPath = tempDir.resolve("xcore.toml");
        write(tomlPath, """
                version = 1

                [server]
                name = "toml-server"
                """);

        Path jsonPath = tempDir.resolve("xcconfig.json");
        write(jsonPath, """
                {
                  "server": "json-server"
                }
                """);

        ConfigFactory factory = new ConfigFactory();

        // Act
        TomlXcoreConfig serverLocalConfig = factory.serverLocalConfig(prettyGson);
        Config config = factory.config(serverLocalConfig);

        // Assert
        assertThat(serverLocalConfig.server.name).isEqualTo("toml-server");
        assertThat(config.server).isEqualTo("toml-server");
    }

    @Test
    @DisplayName("server-local config falls back to legacy xcconfig.json when xcore.toml is absent")
    void config_legacyJsonFallbackWorksWhenTomlAbsent() throws IOException {
        // Arrange
        Path jsonPath = tempDir.resolve("xcconfig.json");
        write(jsonPath, """
                {
                  "server": "legacy-server",
                  "disabled_commands": null,
                  "disabled_features": null,
                  "translation": null
                }
                """);

        ConfigTomlLoader.setBackupTimestampSupplier(() -> LocalDateTime.of(2026, 5, 24, 18, 0, 1));

        ConfigFactory factory = new ConfigFactory();

        // Act
        TomlXcoreConfig serverLocalConfig = factory.serverLocalConfig(prettyGson);
        Config config = factory.config(serverLocalConfig);

        // Assert
        assertThat(serverLocalConfig.server.name).isEqualTo("legacy-server");
        assertThat(config.server).isEqualTo("legacy-server");
        assertThat(config.disabledCommands).isNotNull().isEmpty();
        assertThat(config.disabledFeatures).isNotNull().isEmpty();
        assertThat(config.translation).isNotNull();
        assertThat(config.translation.pipeline).containsExactly("google");
        assertThat(tempDir.resolve("xcconfig.json")).doesNotExist();
        assertThat(tempDir.resolve("xcore.toml")).exists();
        assertThat(tempDir.resolve("xcconfig.json.bak-20260524-180001")).exists();
    }

    @Test
    @DisplayName("global config creates missing secrets.toml from defaults and fails required-field validation")
    void globalConfig_createsMissingSecretsTomlFromDefaultsAndFailsValidation() throws IOException {
        // Arrange
        Path customGlobalDir = Files.createDirectories(tempDir.resolve("global-" + UUID.randomUUID()));
        Config config = new Config();
        config.globalConfigDirectory = customGlobalDir.toString();
        ConfigFactory factory = new ConfigFactory();

        // Act + Assert
        assertThatThrownBy(() -> factory.globalConfig(config, factory.tomlSecretsConfig(config, prettyGson)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required config in secrets.toml")
                .hasMessageContaining("database.mongo_connection_string")
                .hasMessageContaining("database.name");

        Path secretsPath = customGlobalDir.resolve("secrets.toml");
        assertThat(secretsPath).exists();
        assertThat(read(secretsPath))
                .contains("mongo_connection_string = \"\"")
                .contains("name = \"\"");
    }

    @Test
    @DisplayName("global config loads secrets.toml when both TOML and legacy JSON exist")
    void globalConfig_tomlTakesPrecedenceOverLegacyJson() throws IOException {
        // Arrange
        Path customGlobalDir = Files.createDirectories(tempDir.resolve("global-" + UUID.randomUUID()));

        Path tomlPath = customGlobalDir.resolve("secrets.toml");
        write(tomlPath, """
                version = 1

                [database]
                mongo_connection_string = "mongodb://toml:27017"
                name = "toml-db"
                """);

        Path jsonPath = customGlobalDir.resolve("secrets.json");
        write(jsonPath, """
                {
                  "mongo_connection_string": "mongodb://json:27017",
                  "database_name": "json-db"
                }
                """);

        Config config = new Config();
        config.globalConfigDirectory = customGlobalDir.toString();
        ConfigFactory factory = new ConfigFactory();

        // Act
        TomlSecretsConfig tomlSecretsConfig = factory.tomlSecretsConfig(config, prettyGson);
        GlobalConfig globalConfig = factory.globalConfig(config, tomlSecretsConfig);

        // Assert
        assertThat(tomlSecretsConfig.database.mongoConnectionString).isEqualTo("mongodb://toml:27017");
        assertThat(tomlSecretsConfig.database.name).isEqualTo("toml-db");
        assertThat(globalConfig.mongoConnectionString).isEqualTo("mongodb://toml:27017");
        assertThat(globalConfig.databaseName).isEqualTo("toml-db");
    }

    @Test
    @DisplayName("global config falls back to legacy secrets.json when secrets.toml is absent")
    void globalConfig_legacyJsonFallbackWorksWhenTomlAbsent() throws IOException {
        // Arrange
        Path customGlobalDir = Files.createDirectories(tempDir.resolve("global-" + UUID.randomUUID()));

        Path jsonPath = customGlobalDir.resolve("secrets.json");
        write(jsonPath, """
                {
                  "mongo_connection_string": "mongodb://legacy:27017",
                  "database_name": "legacy-db",
                  "translation_providers": null
                }
                """);

        ConfigTomlLoader.setBackupTimestampSupplier(() -> LocalDateTime.of(2026, 5, 24, 18, 0, 2));

        Config config = new Config();
        config.globalConfigDirectory = customGlobalDir.toString();
        ConfigFactory factory = new ConfigFactory();

        // Act
        TomlSecretsConfig tomlSecretsConfig = factory.tomlSecretsConfig(config, prettyGson);
        GlobalConfig globalConfig = factory.globalConfig(config, tomlSecretsConfig);

        // Assert
        assertThat(tomlSecretsConfig.database.mongoConnectionString).isEqualTo("mongodb://legacy:27017");
        assertThat(tomlSecretsConfig.database.name).isEqualTo("legacy-db");
        assertThat(globalConfig.mongoConnectionString).isEqualTo("mongodb://legacy:27017");
        assertThat(globalConfig.databaseName).isEqualTo("legacy-db");
        assertThat(globalConfig.translationProviders).containsOnlyKeys("google");
        assertThat(customGlobalDir.resolve("secrets.json")).doesNotExist();
        assertThat(customGlobalDir.resolve("secrets.toml")).exists();
        assertThat(customGlobalDir.resolve("secrets.json.bak-20260524-180002")).exists();
    }

    @Test
    @DisplayName("global config reads valid secrets from configured directory and normalizes providers")
    void globalConfig_readsValidSecretsFromConfiguredDirectoryAndNormalizesProviders() throws IOException {
        // Arrange
        Path customGlobalDir = Files.createDirectories(tempDir.resolve("global-" + UUID.randomUUID()));
        Path secretsPath = customGlobalDir.resolve("secrets.toml");
        write(secretsPath, """
                version = 1

                [database]
                mongo_connection_string = "mongodb://localhost:27017"
                name = "xcore"

                [translation]
                """);

        Config config = new Config();
        config.globalConfigDirectory = customGlobalDir.toString();
        ConfigFactory factory = new ConfigFactory();

        // Act
        TomlSecretsConfig tomlSecretsConfig = factory.tomlSecretsConfig(config, prettyGson);
        GlobalConfig globalConfig = factory.globalConfig(config, tomlSecretsConfig);

        // Assert
        assertThat(tomlSecretsConfig.database.mongoConnectionString).isEqualTo("mongodb://localhost:27017");
        assertThat(tomlSecretsConfig.database.name).isEqualTo("xcore");
        assertThat(globalConfig.mongoConnectionString).isEqualTo("mongodb://localhost:27017");
        assertThat(globalConfig.databaseName).isEqualTo("xcore");
        assertThat(globalConfig.translationProviders).containsOnlyKeys("google");
        assertThat(secretsPath).exists();
    }

    private static void write(Path path, String content) {
        try {
            Files.writeString(path, content);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
