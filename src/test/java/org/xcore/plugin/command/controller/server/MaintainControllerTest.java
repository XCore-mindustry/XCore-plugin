package org.xcore.plugin.command.controller.server;

import arc.files.Fi;
import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.ServerLocalConfigTomlStore;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerDataCacheReloadCommandV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ServerCommandExecuteCommandV1;
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
        var serverLocalConfig = new TomlXcoreConfig();
        var configFile = mock(Fi.class);
        var tomlStore = new ServerLocalConfigTomlStore(configFile);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                serverLocalConfig,
                tomlStore,
                gson
        );

        controller.gcmd(sender, "say hello world", "mini-pvp,mini-hexed", false);

        var captor = ArgumentCaptor.forClass(ServerCommandExecuteCommandV1.class);
        verify(network).post(captor.capture());

        var event = captor.getValue();
        assertThat(event.command()).isEqualTo("say hello world");
        assertThat(event.targetServers()).containsExactly("mini-pvp", "mini-hexed");
        assertThat(event.exclusion()).isFalse();
    }

    @Test
    @DisplayName("gcmd sends command to all servers when targets are omitted")
    void gcmdFallsBackToAllServers() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var serverLocalConfig = new TomlXcoreConfig();
        var configFile = mock(Fi.class);
        var tomlStore = new ServerLocalConfigTomlStore(configFile);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                serverLocalConfig,
                tomlStore,
                gson
        );

        controller.gcmd(sender, "say hello world", null, false);

        var captor = ArgumentCaptor.forClass(ServerCommandExecuteCommandV1.class);
        verify(network).post(captor.capture());

        var event = captor.getValue();
        assertThat(event.command()).isEqualTo("say hello world");
        assertThat(event.targetServers()).isEmpty();
        assertThat(event.exclusion()).isFalse();
    }

    @Test
    @DisplayName("gcmd preserves exclusion mode with parsed targets")
    void gcmdPreservesExclusionMode() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var serverLocalConfig = new TomlXcoreConfig();
        var configFile = mock(Fi.class);
        var tomlStore = new ServerLocalConfigTomlStore(configFile);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                serverLocalConfig,
                tomlStore,
                gson
        );

        controller.gcmd(sender, "say hello world", "mini-pvp,mini-hexed", true);

        var captor = ArgumentCaptor.forClass(ServerCommandExecuteCommandV1.class);
        verify(network).post(captor.capture());

        var event = captor.getValue();
        assertThat(event.command()).isEqualTo("say hello world");
        assertThat(event.targetServers()).containsExactly("mini-pvp", "mini-hexed");
        assertThat(event.exclusion()).isTrue();
    }

    @Test
    @DisplayName("disable-cmd normalizes command and persists config")
    void disableCmd_normalizesCommandAndPersistsConfig() {
        var network = mock(NetworkService.class);
        var repository = mock(PlayerDataRepository.class);
        var pluginState = new PluginState();
        var sessionService = mock(SessionService.class);
        var auditService = mock(MapIdentityAuditService.class);
        var serverLocalConfig = new TomlXcoreConfig();
        var configFile = mock(Fi.class);
        var tomlStore = new ServerLocalConfigTomlStore(configFile);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                serverLocalConfig,
                tomlStore,
                gson
        );

        controller.disableCmd(sender, " /Help   Me ");

        assertThat(serverLocalConfig.runtime.disabledCommands).containsExactly("help me");
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
        var serverLocalConfig = new TomlXcoreConfig();
        var configFile = mock(Fi.class);
        var tomlStore = new ServerLocalConfigTomlStore(configFile);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                serverLocalConfig,
                tomlStore,
                gson
        );

        controller.disableCmd(sender, "disable-cmd nested");

        assertThat(serverLocalConfig.runtime.disabledCommands).isEmpty();
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
        var serverLocalConfig = new TomlXcoreConfig();
        serverLocalConfig.runtime.disabledFeatures.add("rtv");
        var configFile = mock(Fi.class);
        var tomlStore = new ServerLocalConfigTomlStore(configFile);
        var gson = new Gson();
        var sender = mock(XCoreSender.class);

        var controller = new MaintainController(
                network,
                repository,
                pluginState,
                sessionService,
                auditService,
                serverLocalConfig,
                tomlStore,
                gson
        );

        controller.enableFeature(sender, "rtv");

        assertThat(serverLocalConfig.runtime.disabledFeatures).doesNotContain("rtv");
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
        var serverLocalConfig = new TomlXcoreConfig();
        serverLocalConfig.server.name = "alpha";
        var configFile = mock(Fi.class);
        var tomlStore = new ServerLocalConfigTomlStore(configFile);
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
                serverLocalConfig,
                tomlStore,
                gson
        );

        controller.deleteBots(sender);

        verify(repository).deleteBots();
        verify(topMenuCacheService).invalidateAll();
        var captor = ArgumentCaptor.forClass(PlayerDataCacheReloadCommandV1.class);
        verify(network).post(captor.capture());
        assertThat(captor.getValue().server()).isEqualTo("alpha");
    }
}
