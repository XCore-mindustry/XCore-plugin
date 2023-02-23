package org.xcore.plugin.commands;

import arc.func.Cons2;
import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.net.Administration.PlayerInfo;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.Database;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.modules.models.PlayerData;

import static arc.util.Strings.parseInt;
import static mindustry.Vars.netServer;

public class ServerCommands {
    public static void register(CommandHandler handler) {
        handler.register("reload-config", "Reload config", args -> {
            Config.init();
            GlobalConfig.init();
        });
        handler.register("edit-rating", "<uuid> <+/-/value> [hex/pvp]", "Edit player`s rating.", args -> {
            PlayerInfo info = netServer.admins.getInfoOptional(args[0]);
            char operator = args[1].charAt(0);

            boolean pvp;
            if (args.length >= 3) {
                pvp = args[2].equals("pvp");
            } else {
                pvp = true;
            }

            if (info == null) {
                Log.info("Player not found.");
                return;
            }

            PlayerData data = Database.getPlayerData(info.id);

            if (!data.exists) {
                Log.err("Player in db not found.");
                return;
            }

            Cons2<Integer, Boolean> set = (i, b) -> {
                if (pvp) {
                    data.pvpRating = b ? i : data.pvpRating + i;
                } else {
                    data.hexedWins += b ? i : data.hexedWins + i;
                }
            };

            switch (operator) {
                case '+' -> {
                    int increment = parseInt(args[1].substring(1));
                    set.get(increment, false);
                }
                case '-' -> {
                    int reduce = parseInt(args[1].substring(1));
                    set.get(-reduce, false);
                }
                default -> set.get(parseInt(args[1]), true);
            }

            Database.setPlayerData(data);
            Log.info("'@' rating is now @(pvp), @(hex)", data.nickname, data.pvpRating, data.hexedWins);
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

            Log.info("'@' DB '@': ", info.plainLastName(), data.nickname);
            Log.info("  PvP Rating: @", data.pvpRating);
            Log.info("  Hexed Wins: @", data.hexedWins);
            Log.info("  Translator Language: @", data.translatorLanguage);
        });
    }
}
