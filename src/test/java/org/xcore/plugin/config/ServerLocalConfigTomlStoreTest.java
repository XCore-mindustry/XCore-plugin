package org.xcore.plugin.config;

import arc.files.Fi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerLocalConfigTomlStoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("write persists Config to xcore.toml and round-trips correctly")
    void write_persistsConfig_andRoundTrips() throws IOException {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Fi tomlFile = new Fi(tomlPath.toFile());
        ServerLocalConfigTomlStore store = new ServerLocalConfigTomlStore(tomlFile);

        Config config = new Config();
        config.server = "test-server";
        config.playerLimit = 42;
        config.consoleEnabled = false;
        config.publicHostOverride = "192.168.1.1";
        config.disabledCommands = Set.of("rtv", "maps");
        config.disabledFeatures = Set.of("chat");

        store.write(config);

        assertThat(tomlPath).exists();
        String written = Files.readString(tomlPath);
        assertThat(written).isNotBlank();

        // Round-trip: load back via ConfigTomlLoader
        ConfigTomlLoader.LoadResult<Config> result = ConfigTomlLoader.loadXcoreConfig(
                new Fi(tempDir.toFile()),
                new SerializationFactory().prettyGson()
        );
        assertThat(result.config.server).isEqualTo("test-server");
        assertThat(result.config.playerLimit).isEqualTo(42);
        assertThat(result.config.consoleEnabled).isFalse();
        assertThat(result.config.publicHostOverride).isEqualTo("192.168.1.1");
        assertThat(result.config.disabledCommands).containsExactlyInAnyOrder("rtv", "maps");
        assertThat(result.config.disabledFeatures).containsExactly("chat");
    }

    @Test
    @DisplayName("write normalizes config before persisting")
    void write_normalizesBeforePersisting() {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Fi tomlFile = new Fi(tomlPath.toFile());
        ServerLocalConfigTomlStore store = new ServerLocalConfigTomlStore(tomlFile);

        Config config = new Config();
        config.server = null; // will normalize to "server" via TomlXcoreConfig
        config.disabledCommands = null;

        store.write(config);

        ConfigTomlLoader.LoadResult<Config> result = ConfigTomlLoader.loadXcoreConfig(
                new Fi(tempDir.toFile()),
                new SerializationFactory().prettyGson()
        );
        assertThat(result.config.server).isEqualTo("server");
        assertThat(result.config.disabledCommands).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("constructor throws when tomlFile is null")
    void constructor_throws_whenTomlFileIsNull() {
        assertThatThrownBy(() -> new ServerLocalConfigTomlStore(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tomlFile must not be null");
    }

    @Test
    @DisplayName("write throws when config is null")
    void write_throws_whenConfigIsNull() {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Fi tomlFile = new Fi(tomlPath.toFile());
        ServerLocalConfigTomlStore store = new ServerLocalConfigTomlStore(tomlFile);

        assertThatThrownBy(() -> store.write(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config must not be null");
    }

    @Test
    @DisplayName("file returns the configured TOML file handle")
    void file_returnsConfiguredTomlFile() {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Fi tomlFile = new Fi(tomlPath.toFile());
        ServerLocalConfigTomlStore store = new ServerLocalConfigTomlStore(tomlFile);

        assertThat(store.file()).isEqualTo(tomlFile);
    }
}
