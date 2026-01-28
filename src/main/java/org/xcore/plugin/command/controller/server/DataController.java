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
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ServerContext;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.DatabaseService;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.model.PlayerData;

@Singleton
public class DataController {

    private final DatabaseService database;
    private final Fi configFile;
    private Config config;
    private final Gson prettyGson;
    private final FindService find;

    @Inject
    public DataController(DatabaseService database, @Named("xcConfigFile") Fi configFile,
                          Config config, @Named("pretty") Gson prettyGson,
                          FindService find) {
        this.database = database;
        this.configFile = configFile;
        this.config = config;
        this.prettyGson = prettyGson;
        this.find = find;
    }

    @Command(name = "xconfig", params = "[field] [value]", description = "Configure xcore plugin settings via JSON field mapping.")
    public void xconfig(ServerContext ctx) {
        String json = prettyGson.toJson(config);
        if (ctx.args().length == 0) {
            Log.info(json);
            return;
        }
        if (ctx.args().length < 2) {
            Log.err("Usage: xconfig <field> <value>");
            return;
        }

        String field = ctx.arg(0);
        String value = ctx.arg(1);
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

    @Command(name = "edit-data", params = "<#id/uuid> <field> <value>", description = "Edit PlayerData fields.")
    public void editData(ServerContext ctx) {
        PlayerData data = find.playerData(ctx.arg(0));
        if (data == null) {
            Log.err("Player not found.");
            return;
        }

        String field = ctx.arg(1);
        String value = ctx.arg(2);
        JsonValue root = new JsonReader().parse(prettyGson.toJson(data));

        if (!root.has(field)) {
            Log.err("Field '@' not found in PlayerData.", field);
            return;
        }

        modifyJson(root.get(field), value);
        PlayerData result = prettyGson.fromJson(root.toJson(JsonWriter.OutputType.json), PlayerData.class);
        database.getPlayerDataRepository().save(result);
        Log.info("PlayerData for @ updated. Field '@' -> '@'.", data.nickname, field, value);
    }

    @Command(name = "dbinfo", params = "<#id/uuid>", description = "Show raw JSON info about player.")
    public void dbInfo(ServerContext ctx) {
        PlayerData data = find.playerData(ctx.arg(0));
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
