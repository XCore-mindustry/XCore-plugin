package org.xcore.plugin.commands.controllers.server;

import arc.util.Log;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import arc.util.serialization.JsonWriter;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.ServerContext;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.models.PlayerData;

import static org.xcore.plugin.PluginVars.*;

@SuppressWarnings("unused")
public class DataController {

    @Command(name = "xconfig", params = "[field] [value]", description = "Configure xcore plugin settings via JSON field mapping.")
    public void xconfig(ServerContext ctx) {
        String json = prettyGson.toJson(config);
        if (ctx.args().length == 0) { Log.info(json); return; }
        if (ctx.args().length < 2) { Log.err("Usage: xconfig <field> <value>"); return; }

        String field = ctx.arg(0);
        String value = ctx.arg(1);
        JsonValue root = new JsonReader().parse(json);

        if (!root.has(field)) { Log.err("Field '@' not found in Config.", field); return; }

        modifyJson(root.get(field), value);
        config = prettyGson.fromJson(root.toJson(JsonWriter.OutputType.json), Config.class);
        configFile.writeString(prettyGson.toJson(config));
        Log.info("Config field '@' updated to '@'.", field, value);
    }

    @Command(name = "edit-data", params = "<#id/uuid> <field> <value>", description = "Edit PlayerData fields.")
    public void editData(ServerContext ctx) {
        PlayerData data = Find.playerData(ctx.arg(0));
        if (data == null) { Log.err("Player not found."); return; }

        String field = ctx.arg(1);
        String value = ctx.arg(2);
        JsonValue root = new JsonReader().parse(prettyGson.toJson(data));

        if (!root.has(field)) { Log.err("Field '@' not found in PlayerData.", field); return; }

        modifyJson(root.get(field), value);
        PlayerData result = prettyGson.fromJson(root.toJson(JsonWriter.OutputType.json), PlayerData.class);
        result.save();
        Log.info("PlayerData for @ updated. Field '@' -> '@'.", data.nickname, field, value);
    }

    @Command(name = "dbinfo", params = "<#id/uuid>", description = "Show raw JSON info about player.")
    public void dbInfo(ServerContext ctx) {
        PlayerData data = Find.playerData(ctx.arg(0));
        if (data == null) { Log.err("Player not found."); return; }
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