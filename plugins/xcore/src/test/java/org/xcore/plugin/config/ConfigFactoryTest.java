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
    void cleanConfigFiles() throws IOException {
        Files.deleteIfExists(tempDir.resolve("xcore.toml"));
    }

    @Test
    @DisplayName("serverLocalConfig creates missing xcore.toml from defaults and loads normalized TomlXcoreConfig")
    void serverLocalConfig_createsMissingXcoreTomlFromDefaultsAndLoadsNormalizedConfig() {
        ConfigFactory factory = new ConfigFactory();

        TomlXcoreConfig serverLocalConfig = factory.serverLocalConfig();

        assertThat(serverLocalConfig.server.name).isEqualTo("server");
        assertThat(serverLocalConfig.runtime.disabledCommands).isNotNull().isEmpty();
        assertThat(serverLocalConfig.runtime.disabledFeatures).isNotNull().isEmpty();
        assertThat(serverLocalConfig.translation).isNotNull();
        assertThat(serverLocalConfig.translation.pipeline).containsExactly("google");

        Path tomlPath = tempDir.resolve("xcore.toml");
        assertThat(tomlPath).exists();
        assertThat(read(tomlPath)).contains("name = \"server\"");
    }

    @Test
    @DisplayName("serverLocalConfig loads existing xcore.toml")
    void serverLocalConfig_loadsExistingToml() throws IOException {
        Path tomlPath = tempDir.resolve("xcore.toml");
        write(tomlPath, """
                version = 1

                [server]
                name = "custom-server"
                player_limit = 50
                """);

        ConfigFactory factory = new ConfigFactory();

        TomlXcoreConfig serverLocalConfig = factory.serverLocalConfig();

        assertThat(serverLocalConfig.server.name).isEqualTo("custom-server");
        assertThat(serverLocalConfig.server.playerLimit).isEqualTo(50);
    }

    @Test
    @DisplayName("tomlSecretsConfig creates missing secrets.toml from defaults and fails required-field validation")
    void tomlSecretsConfig_createsMissingSecretsTomlFromDefaultsAndFailsValidation() throws IOException {
        Path customGlobalDir = Files.createDirectories(tempDir.resolve("global-" + UUID.randomUUID()));
        TomlXcoreConfig serverLocalConfig = new TomlXcoreConfig();
        serverLocalConfig.paths.globalConfigDirectory = customGlobalDir.toString();
        ConfigFactory factory = new ConfigFactory();

        assertThatThrownBy(() -> factory.tomlSecretsConfig(serverLocalConfig))
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
    @DisplayName("tomlSecretsConfig reads valid secrets from configured directory and normalizes providers")
    void tomlSecretsConfig_readsValidSecretsFromConfiguredDirectoryAndNormalizesProviders() throws IOException {
        Path customGlobalDir = Files.createDirectories(tempDir.resolve("global-" + UUID.randomUUID()));
        Path secretsPath = customGlobalDir.resolve("secrets.toml");
        write(secretsPath, """
                version = 1

                [database]
                mongo_connection_string = "mongodb://localhost:27017"
                name = "xcore"

                [translation]
                """);

        TomlXcoreConfig serverLocalConfig = new TomlXcoreConfig();
        serverLocalConfig.paths.globalConfigDirectory = customGlobalDir.toString();
        ConfigFactory factory = new ConfigFactory();

        TomlSecretsConfig tomlSecretsConfig = factory.tomlSecretsConfig(serverLocalConfig);

        assertThat(tomlSecretsConfig.database.mongoConnectionString).isEqualTo("mongodb://localhost:27017");
        assertThat(tomlSecretsConfig.database.name).isEqualTo("xcore");
        assertThat(tomlSecretsConfig.translation.providers).containsOnlyKeys("google");
        assertThat(secretsPath).exists();
    }

    @Test
    @DisplayName("factory creates store, path editor, and renderer beans")
    void factory_createsStorePathEditorAndRendererBeans() {
        ConfigFactory factory = new ConfigFactory();

        assertThat(factory.serverLocalConfigTomlStore()).isNotNull();
        assertThat(factory.serverLocalConfigPathEditor(prettyGson)).isNotNull();
        assertThat(factory.serverLocalConfigTomlRenderer()).isNotNull();
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
