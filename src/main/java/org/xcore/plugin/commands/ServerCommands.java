package org.xcore.plugin.commands;

import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.gen.Groups;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.Packets;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.Database;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.utils.*;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import static arc.Core.app;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.config;

public class ServerCommands {
    public static void register(CommandHandler handler) {
        handler.removeCommand("exit");
        handler.register("exit", "Exit the server application.", args -> {
            Log.info("Shutting down server.");
            netServer.kickAll(Packets.KickReason.serverRestarting);
            app.exit();
        });

        handler.register("reload-config", "Reload config", args -> {
            Config.init();
            GlobalConfig.init();
        });

        handler.register("perm", "<name/uuid/ip> <perm> <true/false>", "Give/remove permission.", args -> {
            PlayerInfo info = Find.playerInfo(args[0]);
            String perm = args[1];
            boolean value = Boolean.parseBoolean(args[2]);

            if (info == null) {
                Log.info("Player not found.");
                return;
            }

            PlayerData data = Database.getCached(args[0]);
            boolean cached = true;
            if (data == null) {
                cached = false;
                data = Database.getPlayerData(args[0]);
            }

            if (perm.equals("js-access")) data.jsAccess = value;
            if (perm.equals("console-panel-access")) data.consolePanelAccess = value;

            if (cached) Database.setCached(data);
            Database.setPlayerData(data);
            Log.info("Done.");
        });

        handler.register("dbinfo", "<name/uuid/ip>", "Info about player from db.", args -> {
            PlayerInfo info = Find.playerInfo(args[0]);

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
            Log.info("  Hexed Wins: @", data.hexedPoints);
            Log.info("  Translator Language: @", data.translatorLanguage);
        });

        handler.register("tempban", "<name/uuid/ip> <days-of-ban> <global> <reason...>", "Temporary ban player.", args -> {
            var target = Find.playerInfo(args[0]);

            if (target == null) {
                Log.err("Player not found.");
                return;
            }

            int days = Strings.parseInt(args[1]);

            if (days <= 0) {
                Log.err("Ban days must be a number / positive number.");
                return;
            }

            boolean global = Boolean.parseBoolean(args[2]);

            Groups.player.each(p -> p.uuid().equals(target.id) || p.ip().equals(target.lastIP), p -> p.kick(Packets.KickReason.banned));

            BanData ban = BanData.builder()
                    .uuid(target.id)
                    .ip(global ? target.lastIP : "")
                    .name(target.lastName)
                    .adminName("console")
                    .reason(args[3])
                    .server(global ? "global" : config.server)
                    .unbanDate(Time.millis() + TimeUnit.DAYS.toMillis(days))
                    .build();
            ban.generateBid();

            JavelinCommunicator.sendEvent(ban);
        });
        handler.register("tempbans", "[global]", "List all temporary banned players.", args -> {
            Log.info("Temporary banned players:");
            Seq<BanData> bans = Database.getBanned(args.length > 0);

            bans.each(ban -> {
                var date = LocalDateTime.ofInstant(Instant.ofEpochMilli(ban.unbanDate), ZoneId.systemDefault()).toString();
                Log.info("@:  '@' / Name: @ / IP: '@' / Admin: @ / Unban date: @ / Reason: '@'", ban.bid, ban.uuid, ban.name, ban.ip, ban.adminName, date, ban.reason);
            });
        });

        handler.register("tempunban", "<name/uuid/ip/bid>", "Unban a temporary banned player.", args -> {
            if (args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))) {
                int bid = Strings.parseInt(args[0].substring(1));
                var ban = Database.unBanById(bid);

                if (ban == null) {
                    Log.info("Ban not found.");
                    return;
                }

                netServer.admins.unbanPlayerID(ban.uuid);
                netServer.admins.unbanPlayerIP(ban.ip);
                Log.info("'@' (@) unbanned", ban.name, ban.uuid);
                return;
            }

            var info = Find.playerInfo(args[0]);

            netServer.admins.unbanPlayerID(info.id);
            netServer.admins.unbanPlayerIP(info.lastIP);
            Database.unBan(info.id, info.lastIP);
            Log.info("Unbanned @", info.lastName);
        });
    }
}
