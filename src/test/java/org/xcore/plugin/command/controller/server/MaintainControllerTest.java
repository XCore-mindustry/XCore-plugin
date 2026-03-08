package org.xcore.plugin.command.controller.server;

import arc.files.Fi;
import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MaintainControllerTest {

    @Test
    @DisplayName("gcmd parses comma-separated targets from flag value")
    void gcmdParsesCommaSeparatedTargets() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = new SessionService();
        var config = new Config();
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                config,
                configFile,
                gson
        );

        controller.gcmd(sender, "say hello world", "mini-pvp,mini-hexed", false);

        var captor = ArgumentCaptor.forClass(SocketEvents.ExecuteCommand.class);
        verify(network).post(captor.capture());

        var event = captor.getValue();
        assertThat(event.command()).isEqualTo("say hello world");
        assertThat(event.expectServers()).containsExactly("mini-pvp", "mini-hexed");
        assertThat(event.isExclusion()).isFalse();
    }

    @Test
    @DisplayName("gcmd sends command to all servers when targets are omitted")
    void gcmdFallsBackToAllServers() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = new SessionService();
        var config = new Config();
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                config,
                configFile,
                gson
        );

        controller.gcmd(sender, "say hello world", null, false);

        var captor = ArgumentCaptor.forClass(SocketEvents.ExecuteCommand.class);
        verify(network).post(captor.capture());

        var event = captor.getValue();
        assertThat(event.command()).isEqualTo("say hello world");
        assertThat(event.expectServers()).isEmpty();
        assertThat(event.isExclusion()).isFalse();
    }

    @Test
    @DisplayName("gcmd preserves exclusion mode with parsed targets")
    void gcmdPreservesExclusionMode() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = new SessionService();
        var config = new Config();
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                config,
                configFile,
                gson
        );

        controller.gcmd(sender, "say hello world", "mini-pvp,mini-hexed", true);

        var captor = ArgumentCaptor.forClass(SocketEvents.ExecuteCommand.class);
        verify(network).post(captor.capture());

        var event = captor.getValue();
        assertThat(event.command()).isEqualTo("say hello world");
        assertThat(event.expectServers()).containsExactly("mini-pvp", "mini-hexed");
        assertThat(event.isExclusion()).isTrue();
    }
}
