package org.xcore.plugin.config;

import arc.files.Fi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigTomlLoaderTest {

    @TempDir
    Path tempDir;

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
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(dataDir);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.file.name()).isEqualTo("xcore.toml");
        assertThat(result.config.server.name).isEqualTo("test-server");
        assertThat(result.config.server.playerLimit).isEqualTo(42);
    }

    @Test
    @DisplayName("loadXcoreConfig preserves bare numeric Discord snowflake from existing TOML")
    void loadXcoreConfig_preservesBareNumericDiscordSnowflake() throws IOException {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Files.writeString(tomlPath, """
                version = 1

                [discord]
                channel_id = 1099650307396476958
                """);

        Fi dataDir = new Fi(tempDir.toFile());
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(dataDir);

        assertThat(result.config.discord.channelId).isEqualTo("1099650307396476958");
        assertThat(result.config.discord.channelIdAsLong()).isEqualTo(1099650307396476958L);
    }

    @Test
    @DisplayName("loadXcoreConfig returns DEFAULT_TEMPLATE source and creates file when neither exists")
    void loadXcoreConfig_returnsDefaultTemplateSource_whenNeitherExists() {
        Fi dataDir = new Fi(tempDir.toFile());
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(dataDir);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.DEFAULT_TEMPLATE);
        assertThat(result.file.name()).isEqualTo("xcore.toml");
        assertThat(result.file.exists()).isTrue();
        assertThat(result.config.server.name).isEqualTo("server");
        assertThat(result.config.server.playerLimit).isEqualTo(30);
        assertThat(result.config.server.autoStart).isFalse();
        assertThat(result.config.server.autoStartGamemode).isEqualTo("survival");
    }

    @Test
    @DisplayName("loadXcoreConfig reads auto_start and auto_start_gamemode from TOML")
    void loadXcoreConfig_readsAutoStartFromToml() throws IOException {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Files.writeString(tomlPath, """
                version = 1

                [server]
                auto_start = true
                auto_start_gamemode = "pvp"
                """);

        Fi dataDir = new Fi(tempDir.toFile());
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(dataDir);

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.config.server.autoStart).isTrue();
        assertThat(result.config.server.autoStartGamemode).isEqualTo("pvp");
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

        ConfigTomlLoader.LoadResult<TomlSecretsConfig> result = ConfigTomlLoader.loadTomlSecretsConfig(tempDir.toString());

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(result.file.name()).isEqualTo("secrets.toml");
        assertThat(result.config.database.mongoConnectionString).isEqualTo("mongodb://toml:27017");
        assertThat(result.config.database.name).isEqualTo("toml-db");
    }

    @Test
    @DisplayName("loadTomlSecretsConfig returns DEFAULT_TEMPLATE source and creates file when neither exists")
    void loadTomlSecretsConfig_returnsDefaultTemplateSource_whenNeitherExists() {
        ConfigTomlLoader.LoadResult<TomlSecretsConfig> result = ConfigTomlLoader.loadTomlSecretsConfig(tempDir.toString());

        assertThat(result.source).isEqualTo(ConfigTomlLoader.Source.DEFAULT_TEMPLATE);
        assertThat(result.file.name()).isEqualTo("secrets.toml");
        assertThat(result.file.exists()).isTrue();
        assertThat(result.config.database.mongoConnectionString).isEqualTo("");
        assertThat(result.config.database.name).isEqualTo("");
    }

    @Test
    @DisplayName("loaders ignore the legacy [ip_reputation] section removed in v4.4.0")
    void loaders_ignoreLegacyIpReputationSection() throws IOException {
        Files.writeString(tempDir.resolve("xcore.toml"), """
                version = 1

                [ip_reputation]
                enabled = true
                block_proxy = false
                cache_ttl_seconds = 7200

                [server]
                name = "legacy-ip-rep"
                """);
        Files.writeString(tempDir.resolve("secrets.toml"), """
                version = 1

                [ip_reputation.provider]
                base_url = "http://ip-api.example.com/json"
                timeout_seconds = 20
                max_retries = 5
                rate_limit_per_minute = 60

                [database]
                mongo_connection_string = "mongodb://toml:27017"
                name = "toml-db"
                """);

        Fi dataDir = new Fi(tempDir.toFile());
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> xcore = ConfigTomlLoader.loadXcoreConfig(dataDir);
        ConfigTomlLoader.LoadResult<TomlSecretsConfig> secrets =
                ConfigTomlLoader.loadTomlSecretsConfig(tempDir.toString());

        assertThat(xcore.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(xcore.config.server.name).isEqualTo("legacy-ip-rep");
        assertThat(secrets.source).isEqualTo(ConfigTomlLoader.Source.TOML);
        assertThat(secrets.config.database.mongoConnectionString).isEqualTo("mongodb://toml:27017");
    }

    @Test
    @DisplayName("loaders tolerate unknown sections and default missing ones")
    void loaders_tolerateUnknownSectionsAndMissingFields() throws IOException {
        Files.writeString(tempDir.resolve("xcore.toml"), """
                version = 1

                [some_future_section]
                yet_unknown_key = "value"

                [server]
                name = "lenient-load"
                """);
        Files.writeString(tempDir.resolve("secrets.toml"), """
                version = 1

                [brand_new_section]
                mystery = true
                """);

        Fi dataDir = new Fi(tempDir.toFile());
        ConfigTomlLoader.LoadResult<TomlXcoreConfig> xcore = ConfigTomlLoader.loadXcoreConfig(dataDir);
        ConfigTomlLoader.LoadResult<TomlSecretsConfig> secrets =
                ConfigTomlLoader.loadTomlSecretsConfig(tempDir.toString());

        assertThat(xcore.config.server.name).isEqualTo("lenient-load");
        assertThat(xcore.config.translation.enabled).isTrue();
        assertThat(secrets.config.externalLinks.discordUrl).isEqualTo("https://discord.gg/RUMCCa9QAC");
    }

    @Test
    @DisplayName("resolveXcoreToml points to xcore.toml in data directory")
    void resolveXcoreToml_pointsToCorrectFile() {
        Fi dataDir = new Fi(tempDir.toFile());
        Fi resolved = ConfigTomlLoader.resolveXcoreToml(dataDir);
        assertThat(resolved.name()).isEqualTo("xcore.toml");
        assertThat(resolved.parent().file().toPath()).isEqualTo(tempDir);
    }

    @Test
    @DisplayName("resolveSecretsToml points to secrets.toml in specified directory")
    void resolveSecretsToml_pointsToCorrectFile() {
        Fi resolved = ConfigTomlLoader.resolveSecretsToml(tempDir.toString());
        assertThat(resolved.name()).isEqualTo("secrets.toml");
        assertThat(resolved.parent().file().toPath()).isEqualTo(tempDir);
    }
}
