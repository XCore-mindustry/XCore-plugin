package org.xcore.plugin.command.controller.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.ServerLocalConfigTomlStore;
import org.xcore.plugin.config.TomlXcoreConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RuntimeToggleConfigServiceTest {

    @Test
    @DisplayName("disable stores normalized runtime command toggles on structured config")
    void disable_storesRuntimeCommandTogglesOnStructuredConfig() {
        var config = new TomlXcoreConfig();
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var service = new RuntimeToggleConfigService(config, tomlStore);

        var result = service.disable(RuntimeToggleConfigService.ToggleTarget.COMMAND, "help me");

        assertThat(result.changed()).isTrue();
        assertThat(config.runtime.disabledCommands).containsExactly("help me");
        verify(tomlStore).write(any(TomlXcoreConfig.class));
    }

    @Test
    @DisplayName("enable removes runtime feature toggles from structured config")
    void enable_removesRuntimeFeatureTogglesFromStructuredConfig() {
        var config = new TomlXcoreConfig();
        config.runtime.disabledFeatures.add("rtv");
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var service = new RuntimeToggleConfigService(config, tomlStore);

        var result = service.enable(RuntimeToggleConfigService.ToggleTarget.FEATURE, "rtv");

        assertThat(result.changed()).isTrue();
        assertThat(config.runtime.disabledFeatures).doesNotContain("rtv");
        verify(tomlStore).write(any(TomlXcoreConfig.class));
    }

    @Test
    @DisplayName("disable rolls back runtime command toggle when save fails")
    void disable_rollsBackRuntimeCommandToggleWhenSaveFails() {
        var config = new TomlXcoreConfig();
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var service = new RuntimeToggleConfigService(config, tomlStore);
        doThrow(new IllegalStateException("disk full"))
                .when(tomlStore)
                .write(any(TomlXcoreConfig.class));

        assertThatThrownBy(() -> service.disable(RuntimeToggleConfigService.ToggleTarget.COMMAND, "help"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("disk full");

        assertThat(config.runtime.disabledCommands).doesNotContain("help");
    }

    @Test
    @DisplayName("enable rolls back runtime feature toggle when save fails")
    void enable_rollsBackRuntimeFeatureToggleWhenSaveFails() {
        var config = new TomlXcoreConfig();
        config.runtime.disabledFeatures.add("rtv");
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var service = new RuntimeToggleConfigService(config, tomlStore);
        doThrow(new IllegalStateException("disk full"))
                .when(tomlStore)
                .write(any(TomlXcoreConfig.class));

        assertThatThrownBy(() -> service.enable(RuntimeToggleConfigService.ToggleTarget.FEATURE, "rtv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("disk full");

        assertThat(config.runtime.disabledFeatures).contains("rtv");
    }
}
