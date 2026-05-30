package org.xcore.plugin.command.controller.server;

import arc.util.Log;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import arc.util.serialization.JsonWriter;
import com.google.gson.Gson;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.CommandDescription;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudServerController;
import org.xcore.plugin.config.ServerLocalConfigPathEditor;
import org.xcore.plugin.config.ServerLocalConfigTomlRenderer;
import org.xcore.plugin.config.ServerLocalConfigTomlStore;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.TopMenuCacheService;

@Singleton
public class DataController implements CloudServerController {
    private static final String DISABLED_COMMANDS_PATH = "runtime.disabled_commands";
    private static final String DISABLED_FEATURES_PATH = "runtime.disabled_features";

    private final PlayerDataRepository playerDataRepository;
    private final TomlXcoreConfig config;
    private final Gson prettyGson;
    private final FindService find;
    private final TopMenuCacheService topMenuCacheService;
    private final ServerLocalConfigPathEditor pathEditor;
    private final ServerLocalConfigTomlRenderer tomlRenderer;
    private final ServerLocalConfigTomlStore tomlStore;

    @Inject
    public DataController(PlayerDataRepository playerDataRepository,
                          TomlXcoreConfig config,
                          @Named("pretty") Gson prettyGson,
                          FindService find,
                          TopMenuCacheService topMenuCacheService,
                          ServerLocalConfigPathEditor pathEditor,
                          ServerLocalConfigTomlRenderer tomlRenderer,
                          ServerLocalConfigTomlStore tomlStore) {
        this.playerDataRepository = playerDataRepository;
        this.config = config;
        this.prettyGson = prettyGson;
        this.find = find;
        this.topMenuCacheService = topMenuCacheService;
        this.pathEditor = pathEditor;
        this.tomlRenderer = tomlRenderer;
        this.tomlStore = tomlStore;
    }

    public DataController(PlayerDataRepository playerDataRepository,
                          TomlXcoreConfig config,
                          Gson prettyGson,
                          FindService find,
                          ServerLocalConfigPathEditor pathEditor,
                          ServerLocalConfigTomlRenderer tomlRenderer,
                          ServerLocalConfigTomlStore tomlStore) {
        this(playerDataRepository, config, prettyGson, find, null, pathEditor, tomlRenderer, tomlStore);
    }

    @Command("xconfig")
    @CommandDescription("Displays the current server-local XCore configuration. Use it to discover valid TOML paths and values.")
    public void xconfigShow(XCoreSender sender) {
        Log.info(tomlRenderer.render(config));
    }

    @Command("xconfig <field> <value>")
    @CommandDescription("Modifies a server-local XCore config value by legacy field name or TOML path. Lists may use comma-separated or JSON-array syntax.")
    public void xconfigEdit(XCoreSender sender,
                            @Argument(value = "field", description = "Legacy field name or TOML-style dotted path") String field,
                            @Argument(value = "value", description = "The new value (for example true, 64, redis://..., google,openai, or [\"google\",\"openai\"])") @Greedy String value) {
        if (isDedicatedRuntimeTogglePath(field)) {
            Log.err("Path '@' is managed by dedicated toggle commands. Use disable-cmd/enable-cmd or disable-feature/enable-feature.", field);
            return;
        }

        TomlXcoreConfig updated;
        try {
            updated = pathEditor.update(config, field, value);
        } catch (IllegalArgumentException e) {
            Log.err("@", e.getMessage());
            return;
        }

        if (updated == null) {
            Log.err("Field '@' not found in Config. Use legacy aliases or TOML-style dotted paths such as 'server.player_limit' or 'transport.redis.url'.", field);
            return;
        }

        try {
            tomlStore.write(updated);
        } catch (Exception e) {
            Log.err("Failed to persist config change for field '@': @", field, e.getMessage());
            return;
        }

        applyUpdatedConfig(updated);
        Log.info("Config field '@' updated.", field);
    }

    @Command("edit-data <player> <field> <value>")
    @CommandDescription("Directly modifies a field in a player's database entry.")
    public void editData(XCoreSender sender,
                         @Argument(value = "player", description = "Player Name/#ID/UUID") String player,
                         @Argument(value = "field", description = "The database field name") String field,
                         @Argument(value = "value", description = "The new value") @Greedy String value) {

        PlayerData data = find.playerData(player);
        if (data == null) {
            Log.err("Player not found.");
            return;
        }

        JsonValue root = new JsonReader().parse(prettyGson.toJson(data));

        if (!root.has(field)) {
            Log.err("Field '@' not found in PlayerData.", field);
            return;
        }

        modifyJson(root.get(field), value);
        PlayerData result = prettyGson.fromJson(root.toJson(JsonWriter.OutputType.json), PlayerData.class);
        result.id = data.id;
        if (playerDataRepository.save(result) && topMenuCacheService != null) {
            topMenuCacheService.invalidateAll();
        }
        Log.info("PlayerData for @ updated. Field '@' -> '@'.", data.nickname, field, value);
    }

    private boolean isDedicatedRuntimeTogglePath(String field) {
        if (field == null) {
            return false;
        }

        return switch (field.trim()) {
            case "disabledCommands", DISABLED_COMMANDS_PATH -> true;
            case "disabledFeatures", DISABLED_FEATURES_PATH -> true;
            default -> false;
        };
    }

    @Command("dbinfo <player>")
    @CommandDescription("Displays raw database information (JSON) for a player.")
    public void dbInfo(XCoreSender sender,
                       @Argument(value = "player", description = "Player Name/#ID/UUID") String player) {

        PlayerData data = find.playerData(player);
        if (data == null) {
            Log.err("Player not found.");
            return;
        }
        Log.info(prettyGson.toJson(data));
    }

    private void modifyJson(JsonValue jfield, String value) {
        switch (jfield.type()) {
            case stringValue -> jfield.set(value);
            case booleanValue -> jfield.set(Boolean.parseBoolean(value));
            case longValue -> jfield.set(Long.parseLong(value), null);
            case doubleValue -> jfield.set(Double.parseDouble(value), null);
        }
    }

    private void applyUpdatedConfig(TomlXcoreConfig updated) {
        config.version = updated.version;
        config.server = updated.server;
        config.paths = updated.paths;
        config.discord = updated.discord;
        config.transport = updated.transport;
        config.runtime = updated.runtime;
        config.eventHub = updated.eventHub;
        config.translation = updated.translation;
        config.ipReputation = updated.ipReputation;
    }
}
