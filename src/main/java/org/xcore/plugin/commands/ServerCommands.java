package org.xcore.plugin.commands;

import arc.util.CommandHandler;
import arc.util.Log;
import org.xcore.plugin.comp.Config;
import mindustry.net.Administration.PlayerInfo;
import org.xcore.plugin.comp.Database;
import org.xcore.plugin.comp.PlayerData;

import static arc.util.Strings.parseInt;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.*;
public class ServerCommands {
    public static void register(CommandHandler handler) {
        handler.register("reload-config", "Reload config", args -> config = gson.fromJson(configFile.reader(), Config.class));
        if (config.isMiniPvP()) {
            handler.register("edit-rating", "<uuid> <+/-/value>", "Edit player`s rating.", args -> {
                PlayerInfo info = netServer.admins.getInfoOptional(args[0]);
                char operator = args[1].charAt(0);

                if (info == null) {
                    Log.info("Player not found.");
                    return;
                }

                PlayerData data = Database.getPlayerData(info.id);

                if (!data.exists) {
                    Log.err("Player in db not found.");
                    return;
                }

                switch (operator) {
                    case '+' -> {
                        int increment = parseInt(args[1].substring(1));
                        data.rating += increment;
                    }
                    case '-' -> {
                        int reduce = parseInt(args[1].substring(1));
                        data.rating -= reduce;
                    }
                    default -> data.rating = parseInt(args[1]);
                }

                Database.setPlayerData(data);
                Log.info("'@' rating is now @", data.nickname, data.rating);
            });

            handler.register("dbinfo", "<uuid>", "Info about player from db.", args -> {
                PlayerInfo info = netServer.admins.getInfoOptional(args[0]);

                if (info == null) {
                    Log.info("Player not found.");
                    return;
                }

                PlayerData data = Database.getPlayerData(info.id);

                if (!data.exists) {
                    Log.err("Player in db not found.");
                    return;
                }

                Log.info(info.plainLastName() + ":");
                Log.info("  Rating: " + data.rating);
            });
        }
    }
}
