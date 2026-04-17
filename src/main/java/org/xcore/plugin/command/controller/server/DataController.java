package org.xcore.plugin.command.controller.server;

import arc.files.Fi;
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
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.TopMenuCacheService;

@Singleton
public class DataController implements CloudServerController {

    private final PlayerDataRepository playerDataRepository;
    private final Fi configFile;
    private Config config;
    private final Gson prettyGson;
    private final FindService find;
    private final TopMenuCacheService topMenuCacheService;

    @Inject
    public DataController(PlayerDataRepository playerDataRepository,
                          @Named("xcConfigFile") Fi configFile,
                          Config config,
                          @Named("pretty") Gson prettyGson,
                          FindService find,
                          TopMenuCacheService topMenuCacheService) {
        this.playerDataRepository = playerDataRepository;
        this.configFile = configFile;
        this.config = config;
        this.prettyGson = prettyGson;
        this.find = find;
        this.topMenuCacheService = topMenuCacheService;
    }

    public DataController(PlayerDataRepository playerDataRepository,
                          Fi configFile,
                          Config config,
                          Gson prettyGson,
                          FindService find) {
        this(playerDataRepository, configFile, config, prettyGson, find, null);
    }

    @Command("xconfig")
    @CommandDescription("Displays the current XCore configuration JSON.")
    public void xconfigShow(XCoreSender sender) {
        Log.info(prettyGson.toJson(config));
    }

    @Command("xconfig <field> <value>")
    @CommandDescription("Modifies a specific field in the XCore configuration.")
    public void xconfigEdit(XCoreSender sender,
                            @Argument(value = "field", description = "The JSON field name") String field,
                            @Argument(value = "value", description = "The new value") @Greedy String value) {

        String json = prettyGson.toJson(config);
        JsonValue root = new JsonReader().parse(json);

        if (!root.has(field)) {
            Log.err("Field '@' not found in Config.", field);
            return;
        }

        modifyJson(root.get(field), value);
        config = prettyGson.fromJson(root.toJson(JsonWriter.OutputType.json), Config.class);
        configFile.writeString(prettyGson.toJson(config));
        Log.info("Config field '@' updated to '@'.", field, value);
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
}
