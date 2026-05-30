package org.xcore.plugin.command.controller.server;

import com.google.gson.Gson;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.SerializationFactory;
import org.xcore.plugin.config.ServerLocalConfigPathEditor;
import org.xcore.plugin.config.ServerLocalConfigTomlRenderer;
import org.xcore.plugin.config.ServerLocalConfigTomlStore;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.TopMenuCacheService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataControllerTest {

    @Test
    @DisplayName("xconfigEdit updates in-memory config and persists through TOML store")
    void xconfigEdit_updatesConfigAndPersistsThroughTomlStore() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = new ServerLocalConfigPathEditor(gson);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "playerLimit", "64");

        var configCaptor = ArgumentCaptor.forClass(TomlXcoreConfig.class);
        verify(tomlStore).write(configCaptor.capture());
        assertThat(configCaptor.getValue().server.playerLimit).isEqualTo(64);
        assertThat(config.server.playerLimit).isEqualTo(64);
    }

    @Test
    @DisplayName("xconfigEdit supports nested TOML-style paths")
    void xconfigEdit_supportsNestedTomlPaths() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = new ServerLocalConfigPathEditor(gson);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "transport.redis.url", "redis://example:6379");

        var configCaptor = ArgumentCaptor.forClass(TomlXcoreConfig.class);
        verify(tomlStore).write(configCaptor.capture());
        assertThat(configCaptor.getValue().transport.redis.url).isEqualTo("redis://example:6379");
        assertThat(config.transport.redis.url).isEqualTo("redis://example:6379");
    }

    @Test
    @DisplayName("xconfigEdit supports comma-separated translation pipeline updates")
    void xconfigEdit_supportsCommaSeparatedTranslationPipeline() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = new ServerLocalConfigPathEditor(gson);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "translation.pipeline", "google, openai, deepl");

        var configCaptor = ArgumentCaptor.forClass(TomlXcoreConfig.class);
        verify(tomlStore).write(configCaptor.capture());
        assertThat(configCaptor.getValue().translation.pipeline)
                .containsExactly("google", "openai", "deepl");
        assertThat(config.translation.pipeline)
                .containsExactly("google", "openai", "deepl");
    }

    @Test
    @DisplayName("xconfigEdit supports array-style translation pipeline updates")
    void xconfigEdit_supportsJsonArrayTranslationPipeline() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = new ServerLocalConfigPathEditor(gson);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "translation.pipeline", "[\"google\", \"openai\"]");

        var configCaptor = ArgumentCaptor.forClass(TomlXcoreConfig.class);
        verify(tomlStore).write(configCaptor.capture());
        assertThat(configCaptor.getValue().translation.pipeline)
                .containsExactly("google", "openai");
        assertThat(config.translation.pipeline)
                .containsExactly("google", "openai");
    }

    @Test
    @DisplayName("xconfigEdit does not persist when field is missing")
    void xconfigEdit_doesNotPersistWhenFieldIsMissing() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = new ServerLocalConfigPathEditor(gson);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "missing_field", "value");

        verify(tomlStore, never()).write(any(TomlXcoreConfig.class));
        assertThat(config.server.playerLimit).isEqualTo(30);
    }

    @Test
    @DisplayName("xconfigEdit does not persist when integer value is invalid")
    void xconfigEdit_doesNotPersistWhenIntegerValueIsInvalid() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = new ServerLocalConfigPathEditor(gson);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "playerLimit", "not-a-number");

        verify(tomlStore, never()).write(any(TomlXcoreConfig.class));
        assertThat(config.server.playerLimit).isEqualTo(30);
    }

    @Test
    @DisplayName("xconfigEdit does not persist when boolean value is invalid")
    void xconfigEdit_doesNotPersistWhenBooleanValueIsInvalid() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = new ServerLocalConfigPathEditor(gson);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "consoleEnabled", "maybe");

        verify(tomlStore, never()).write(any(TomlXcoreConfig.class));
        assertThat(config.server.consoleEnabled).isTrue();
    }

    @Test
    @DisplayName("xconfigEdit does not persist when translation pipeline array is malformed")
    void xconfigEdit_doesNotPersistWhenTranslationPipelineArrayIsMalformed() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = new ServerLocalConfigPathEditor(gson);

        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "translation.pipeline", "[\"google\",");

        verify(tomlStore, never()).write(any(TomlXcoreConfig.class));
        assertThat(config.translation.pipeline).containsExactly("google");
    }

    @Test
    @DisplayName("xconfigEdit rejects dedicated disabled command paths")
    void xconfigEdit_rejectsDedicatedDisabledCommandPaths() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = mock(ServerLocalConfigPathEditor.class);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "runtime.disabled_commands", "[\"help\"]");

        verify(tomlStore, never()).write(any(TomlXcoreConfig.class));
        verify(pathEditor, never()).update(any(TomlXcoreConfig.class), any(String.class), any(String.class));
        assertThat(config.runtime.disabledCommands).isEmpty();
    }

    @Test
    @DisplayName("xconfigEdit rejects dedicated disabled feature paths")
    void xconfigEdit_rejectsDedicatedDisabledFeaturePaths() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = mock(ServerLocalConfigPathEditor.class);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);
        controller.xconfigEdit(sender, "disabledFeatures", "[\"rtv\"]");

        verify(tomlStore, never()).write(any(TomlXcoreConfig.class));
        verify(pathEditor, never()).update(any(TomlXcoreConfig.class), any(String.class), any(String.class));
        assertThat(config.runtime.disabledFeatures).isEmpty();
    }

    @Test
    @DisplayName("editData preserves player _id when saving updated payload")
    void editDataPreservesMongoId() {
        var repository = mock(PlayerDataRepository.class);
        var topMenuCacheService = mock(TopMenuCacheService.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        var gson = new Gson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = mock(ServerLocalConfigPathEditor.class);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var player = new PlayerData("u-1", true);
        player.pid = 0;
        player.nickname = "pizduk";
        player.pvpRating = 123;
        player.id = new ObjectId();

        when(find.playerData("#0")).thenReturn(player);
        when(repository.save(org.mockito.ArgumentMatchers.any(PlayerData.class))).thenReturn(true);

        var controller = new DataController(repository, config, gson, find, topMenuCacheService, pathEditor, tomlRenderer, tomlStore);
        controller.editData(sender, "#0", "pvpRating", "0");

        var captor = ArgumentCaptor.forClass(PlayerData.class);
        verify(repository).save(captor.capture());

        var saved = captor.getValue();
        assertThat(saved.id).isEqualTo(player.id);
        assertThat(saved.pvpRating).isEqualTo(0);
        assertThat(saved.uuid).isEqualTo("u-1");
        verify(topMenuCacheService).invalidateAll();
    }

    @Test
    @DisplayName("xconfigShow renders TOML-shaped server-local config")
    void xconfigShow_rendersTomlShapedServerLocalConfig() {
        var repository = mock(PlayerDataRepository.class);
        var tomlStore = mock(ServerLocalConfigTomlStore.class);
        var config = new TomlXcoreConfig();
        config.server.name = "alpha";
        config.server.playerLimit = 64;
        config.transport.redis.url = "redis://example:6379";
        var gson = new SerializationFactory().prettyGson();
        var find = mock(FindService.class);
        var sender = mock(XCoreSender.class);
        var pathEditor = new ServerLocalConfigPathEditor(gson);
        var tomlRenderer = new ServerLocalConfigTomlRenderer();

        var controller = new DataController(repository, config, gson, find, pathEditor, tomlRenderer, tomlStore);

        assertThat(tomlRenderer.render(config))
                .contains("version = 1")
                .contains("[server]")
                .contains("name = \"alpha\"")
                .contains("player_limit = 64")
                .contains("[transport.redis]")
                .contains("url = \"redis://example:6379\"")
                .doesNotContain("server.name =")
                .doesNotContain("transport.redis.url =");

        controller.xconfigShow(sender);

        verify(tomlStore, never()).write(any(TomlXcoreConfig.class));
    }
}
