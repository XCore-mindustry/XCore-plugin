package org.xcore.plugin.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigEnvironmentTest {
    @Test void leavesFileValuesUntouchedWithoutOverrides() {
        var config = new TomlXcoreConfig();
        config.server.name = "custom";
        config.server.autoStart = false;
        ConfigFactory.applyEnvironmentOverrides(config, Map.of());
        assertThat(config.server.name).isEqualTo("custom");
        assertThat(config.server.autoStart).isFalse();
    }

    @Test void appliesExplicitRuntimeOverridesWithoutWritingConfig() {
        var config = new TomlXcoreConfig();
        ConfigFactory.applyEnvironmentOverrides(config, Map.of(
            "MINDUSTRY_SERVER_NAME", "dev-hexed", "MINDUSTRY_GAMEMODE", "pvp", "XCORE_AUTO_START", "false"));
        assertThat(config.server.name).isEqualTo("dev-hexed");
        assertThat(config.server.autoStartGamemode).isEqualTo("pvp");
        assertThat(config.server.autoStart).isFalse();
        ConfigFactory.applyEnvironmentOverrides(config, Map.of("XCORE_AUTO_START", "true"));
        assertThat(config.server.autoStart).isTrue();
    }

    @Test void rejectsInvalidBooleanWithoutEchoingTheValue() {
        assertThatThrownBy(() -> ConfigFactory.applyEnvironmentOverrides(new TomlXcoreConfig(),
            Map.of("XCORE_AUTO_START", "sensitive-invalid-value")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("XCORE_AUTO_START must be true or false");
    }
}
