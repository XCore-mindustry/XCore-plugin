package org.xcore.plugin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerLocalConfigTomlRendererTest {

    private final ServerLocalConfigTomlRenderer renderer = new ServerLocalConfigTomlRenderer();

    @Test
    @DisplayName("render outputs TOML for TomlXcoreConfig directly")
    void render_outputsTomlForTomlXcoreConfigDirectly() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "alpha";
        config.server.playerLimit = 64;
        config.transport.redis.url = "redis://example:6379";
        config.telemetry.enabled = true;
        config.telemetry.publishIntervalMs = 10000;

        assertThat(renderer.render(config))
                .contains("version = 1")
                .contains("[server]")
                .contains("name = \"alpha\"")
                .contains("player_limit = 64")
                .contains("[transport.redis]")
                .contains("url = \"redis://example:6379\"")
                .contains("[telemetry]")
                .contains("enabled = true")
                .contains("publish_interval_ms = 10000")
                .doesNotContain("server.name =")
                .doesNotContain("transport.redis.url =");
    }

    @Test
    @DisplayName("render throws when TomlXcoreConfig is null")
    void render_throwsWhenTomlXcoreConfigIsNull() {
        assertThatThrownBy(() -> renderer.render((TomlXcoreConfig) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("config must not be null");
    }
}
