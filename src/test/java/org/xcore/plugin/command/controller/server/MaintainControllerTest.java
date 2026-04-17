package org.xcore.plugin.command.controller.server;

import arc.files.Fi;
import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.MapIdentityAuditService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.TopMenuCacheService;
import org.xcore.plugin.common.PluginState;
import org.xcore.plugin.session.SessionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaintainControllerTest {

    @Test
    @DisplayName("gcmd parses comma-separated targets from flag value")
    void gcmdParsesCommaSeparatedTargets() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var config = new Config();
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
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
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var config = new Config();
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
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
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var config = new Config();
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
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

    @Test
    @DisplayName("disable-cmd normalizes command and persists config")
    void disableCmd_normalizesCommandAndPersistsConfig() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var config = new Config();
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                config,
                configFile,
                gson
        );

        controller.disableCmd(sender, " /Help   Me ");

        assertThat(config.disabledCommands).containsExactly("help me");
        verify(configFile).writeString(anyString());
    }

    @Test
    @DisplayName("disable-cmd does not persist protected commands")
    void disableCmd_doesNotPersistProtectedCommands() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var config = new Config();
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                config,
                configFile,
                gson
        );

        controller.disableCmd(sender, "disable-cmd nested");

        assertThat(config.disabledCommands).isEmpty();
        verify(configFile, never()).writeString(anyString());
    }

    @Test
    @DisplayName("enable-feature removes disabled feature and persists config")
    void enableFeature_removesDisabledFeatureAndPersistsConfig() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var config = new Config();
        config.disabledFeatures.add("rtv");
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                config,
                configFile,
                gson
        );

        controller.enableFeature(sender, "rtv");

        assertThat(config.disabledFeatures).doesNotContain("rtv");
        verify(configFile).writeString(anyString());
    }

    @Test
    @DisplayName("deleteBots invalidates top cache when players are removed")
    void deleteBots_invalidatesTopCacheWhenPlayersAreRemoved() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var topMenuCacheService = mock(TopMenuCacheService.class);
        var config = new Config();
        var configFile = mock(Fi.class);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        when(repository.deleteBots()).thenReturn(3L);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                topMenuCacheService,
                config,
                configFile,
                gson
        );

        controller.deleteBots(sender);

        verify(repository).deleteBots();
        verify(topMenuCacheService).invalidateAll();
        verify(network).post(org.mockito.ArgumentMatchers.any(SocketEvents.ReloadPlayerDataCache.class));
    }
}
