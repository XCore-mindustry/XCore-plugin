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
    void cleanConfigFile() throws IOException {
        Files.deleteIfExists(tempDir.resolve("xcconfig.json"));
    }

    @Test
    @DisplayName("config creates missing xcconfig file with normalized defaults")
    void config_createsMissingXcconfigFileWithNormalizedDefaults() {
        // Arrange
        ConfigFactory factory = new ConfigFactory();

        // Act
        Config config = factory.config(prettyGson);

        // Assert
        assertThat(config.server).isEqualTo("server");
        assertThat(config.disabledCommands).isNotNull().isEmpty();
        assertThat(config.disabledFeatures).isNotNull().isEmpty();
        assertThat(config.translation).isNotNull();
        assertThat(config.translation.pipeline).containsExactly("google");

        Path configPath = tempDir.resolve("xcconfig.json");
        assertThat(configPath).exists();
        assertThat(read(configPath)).contains("\"server\": \"server\"");
    }

    @Test
    @DisplayName("config normalizes existing xcconfig file when nullable sections are missing")
    void config_normalizesExistingXcconfigFileWhenNullableSectionsAreMissing() {
        // Arrange
        Path configPath = tempDir.resolve("xcconfig.json");
        write(configPath, """
                {
                  \"server\": \"event\",
                  \"disabled_commands\": null,
                  \"disabled_features\": null,
                  \"translation\": null
                }
                """);
        ConfigFactory factory = new ConfigFactory();

        // Act
        Config config = factory.config(prettyGson);

        // Assert
        assertThat(config.server).isEqualTo("event");
        assertThat(config.disabledCommands).isNotNull().isEmpty();
        assertThat(config.disabledFeatures).isNotNull().isEmpty();
        assertThat(config.translation).isNotNull();
        assertThat(config.translation.pipeline).containsExactly("google");
    }

    @Test
    @DisplayName("global config creates missing secrets file in configured directory before failing validation")
    void globalConfig_createsMissingSecretsFileInConfiguredDirectoryBeforeFailingValidation() throws IOException {
        // Arrange
        Path customGlobalDir = Files.createDirectories(tempDir.resolve("global-" + UUID.randomUUID()));
        Config config = new Config();
        config.globalConfigDirectory = customGlobalDir.toString();
        ConfigFactory factory = new ConfigFactory();

        // Act + Assert
        assertThatThrownBy(() -> factory.globalConfig(config, prettyGson))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing required config in secrets.json")
                .hasMessageContaining("mongo_connection_string")
                .hasMessageContaining("database_name");

        Path secretsPath = customGlobalDir.resolve("secrets.json");
        assertThat(secretsPath).exists();
        assertThat(read(secretsPath))
                .contains("\"mongo_connection_string\": null")
                .contains("\"database_name\": null");
    }

    @Test
    @DisplayName("global config reads valid secrets from configured directory and normalizes providers")
    void globalConfig_readsValidSecretsFromConfiguredDirectoryAndNormalizesProviders() throws IOException {
        // Arrange
        Path customGlobalDir = Files.createDirectories(tempDir.resolve("global-" + UUID.randomUUID()));
        Path secretsPath = customGlobalDir.resolve("secrets.json");
        write(secretsPath, """
                {
                  \"mongo_connection_string\": \"mongodb://localhost:27017\",
                  \"database_name\": \"xcore\",
                  \"translation_providers\": null
                }
                """);

        Config config = new Config();
        config.globalConfigDirectory = customGlobalDir.toString();
        ConfigFactory factory = new ConfigFactory();

        // Act
        GlobalConfig globalConfig = factory.globalConfig(config, prettyGson);

        // Assert
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
