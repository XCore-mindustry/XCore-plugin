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
    @DisplayName("write persists TomlXcoreConfig directly and round-trips correctly")
    void write_persistsTomlXcoreConfig_andRoundTrips() throws IOException {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Fi tomlFile = new Fi(tomlPath.toFile());
        ServerLocalConfigTomlStore store = new ServerLocalConfigTomlStore(tomlFile);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "test-server";
        config.server.playerLimit = 42;
        config.server.gameStartedTimer = false;
        config.server.publicHostOverride = "192.168.1.1";
        config.runtime.disabledCommands = Set.of("rtv", "maps");
        config.runtime.disabledFeatures = Set.of("chat");

        store.write(config);

        assertThat(tomlPath).exists();
        String written = Files.readString(tomlPath);
        assertThat(written).isNotBlank();
        assertThat(written)
                .contains("[server]")
                .contains("name = \"test-server\"")
                .contains("[runtime]")
                .contains("disabled_commands =")
                .doesNotContain("server.name =")
                .doesNotContain("runtime.disabled_commands =");

        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(
                new Fi(tempDir.toFile()),
                new SerializationFactory().prettyGson()
        );
        assertThat(result.config.server.name).isEqualTo("test-server");
        assertThat(result.config.server.playerLimit).isEqualTo(42);
        assertThat(result.config.server.gameStartedTimer).isFalse();
        assertThat(result.config.server.publicHostOverride).isEqualTo("192.168.1.1");
        assertThat(result.config.runtime.disabledCommands).containsExactlyInAnyOrder("rtv", "maps");
        assertThat(result.config.runtime.disabledFeatures).containsExactly("chat");
    }

    @Test
    @DisplayName("write normalizes TomlXcoreConfig before persisting")
    void write_normalizesTomlXcoreConfigBeforePersisting() {
        Path tomlPath = tempDir.resolve("xcore.toml");
        Fi tomlFile = new Fi(tomlPath.toFile());
        ServerLocalConfigTomlStore store = new ServerLocalConfigTomlStore(tomlFile);

        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = null;
        config.runtime.disabledCommands = null;

        store.write(config);

        ConfigTomlLoader.LoadResult<TomlXcoreConfig> result = ConfigTomlLoader.loadXcoreConfig(
                new Fi(tempDir.toFile()),
                new SerializationFactory().prettyGson()
        );
        assertThat(result.config.server.name).isEqualTo("server");
        assertThat(result.config.runtime.disabledCommands).isNotNull().isEmpty();
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

        assertThatThrownBy(() -> store.write((TomlXcoreConfig) null))
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
