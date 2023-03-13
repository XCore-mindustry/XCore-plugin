package org.xcore.plugin.commands;

import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import com.mongodb.client.result.DeleteResult;
import mindustry.gen.Groups;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.Packets;
import org.xcore.plugin.utils.*;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
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

        handler.register("perm", "<uuid> <perm> <true/false>", "Give/remove permission.", args -> {
            PlayerInfo info = netServer.admins.getInfoOptional(args[0]);
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

            if (cached) Database.getCached(args[0]);
            Database.setPlayerData(data);
            Log.info("Done.");
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
            Log.info("  Hexed Wins: @", data.hexedPoints);
            Log.info("  Translator Language: @", data.translatorLanguage);
        });

        handler.register("tempban", "<uuid> <days-of-ban> <banByIP?/reason...>", "Temporary ban player.", args -> {
            var target = netServer.admins.getInfoOptional(args[0]);

            if (target == null) {
                Log.err("Player not found.");
                return;
            }

            int days = Strings.parseInt(args[1]);

            if (days <= 0) {
                Log.err("Ban days must be a number / positive number.");
                return;
            }

            boolean banByIP = Boolean.parseBoolean(args[2].split(" ")[0]);
            String reason = null;
            if (banByIP) {
                String[] toCopy = args[2].split(" ");
                reason = Strings.join(" ", Arrays.copyOfRange(toCopy, 1, toCopy.length));
            }

            Groups.player.each(p -> p.uuid().equals(target.id) || p.ip().equals(target.lastIP), p -> p.kick(Packets.KickReason.banned));

            BanData ban = BanData.builder()
                    .uuid(target.id)
                    .ip(banByIP ? target.lastIP : "")
                    .name(target.lastName)
                    .adminName("console")
                    .reason(banByIP ? reason : args[2])
                    .server(config.server)
                    .unbanDate(Time.millis() + TimeUnit.DAYS.toMillis(days))
                    .build();
            ban.generateBid();

            JavelinCommunicator.sendEvent(ban, Utils::temporaryBan);
        });
        handler.register("tempbans", "List all temporary banned players.", args -> {
            Log.info("Temporary banned players:");
            Seq<BanData> bans = Database.getBanned();

            bans.each(ban -> {
                var date = LocalDateTime.ofInstant(Instant.ofEpochMilli(ban.unbanDate), ZoneId.systemDefault()).toString();
                Log.info("@:  '@' / IP: '@' / Admin: @ / Unban date: @ / Reason: '@'", ban.bid, ban.uuid, ban.ip, ban.adminName, date, ban.reason);
            });
        });

        handler.register("tempunban", "<uuid/ip/bid>", "Unban a temporary banned player.", args -> {
            try {
                long bid = Long.parseLong(args[0]);
                var ban = Database.unBanById(bid);

                if (ban == null) {
                    Log.info("Ban not found.");
                    return;
                }

                netServer.admins.unbanPlayerID(ban.uuid);
                netServer.admins.unbanPlayerIP(ban.ip);
                Log.info("'@' (@) unbanned", ban.name, ban.uuid);
                return;
            } catch (NumberFormatException ignored) {
            }

            netServer.admins.unbanPlayerID(args[0]);
            netServer.admins.unbanPlayerIP(args[0]);
            DeleteResult result = Database.unBan(args[0], "");
            if (result.getDeletedCount() < 1) Database.unBan("", args[0]);

            Log.info("Unbanned.");
        });
    }
}
