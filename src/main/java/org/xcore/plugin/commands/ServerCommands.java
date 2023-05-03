package org.xcore.plugin.commands;

import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import arc.util.serialization.JsonWriter;
import mindustry.gen.Groups;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.Packets;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.JavelinCommunicator;
import org.xcore.plugin.utils.models.IPBanData;
import org.xcore.plugin.utils.models.PlayerData;
import org.xcore.plugin.utils.models.UUIDBanData;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import static arc.Core.app;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.*;

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

        handler.register("edit-data", "<name/uuid/ip> <perm> <value>", "Give/remove permission.", args -> {
            PlayerInfo info = Find.playerInfo(args[0]);
            String field = args[1];
            String value = args[2];

            if (info == null) {
                Log.info("Player not found.");
                return;
            }

            PlayerData data = database.getCached(info.id);
            boolean cached = true;
            if (data == null) {
                cached = false;
                data = database.getPlayerDataExecutor().getPlayerData(info.id);
            }

            String json = gson.toJson(data);
            JsonValue reader = new JsonReader().parse(json);

            if (!reader.has(field)) {
                Log.err("Field '@' not found", field);
                return;
            }

            JsonValue jfield = reader.get(field);

            switch (jfield.type()) {
                case stringValue -> jfield.set(value);
                case booleanValue -> jfield.set(Boolean.parseBoolean(value));
                case longValue -> jfield.set(Long.parseLong(value), null);
            }

            PlayerData result = gson.fromJson(reader.toJson(JsonWriter.OutputType.json), PlayerData.class);

            if (cached) database.setCached(result);
            database.getPlayerDataExecutor().setPlayerData(result);
            Log.info("Done.");
        });

        handler.register("dbinfo", "<name/uuid/ip>", "Info about player from db.", args -> {
            PlayerInfo info = Find.playerInfo(args[0]);

            if (info == null) {
                Log.info("Player not found.");
                return;
            }

            PlayerData data = database.getCached(info.id);
            data = data == null ? database.getPlayerDataExecutor().getPlayerData(info.id) : data;

            Log.info(gson.toJson(data));
        });

        handler.register("tempban", "<name/uuid/ip> <days-of-ban> <global> <reason...>", "Temporary ban player.", args -> {
            var target = Find.playerInfo(args[0]);

            if (target == null) {
                Log.err("Player not found.");
                return;
            }

            int days = Strings.parseInt(args[1]);
            long unbanDate = Time.millis() + TimeUnit.DAYS.toMillis(days);

            if (days <= 0) {
                Log.err("Ban days must be a number / positive number.");
                return;
            }

            boolean global = args[2].equals("1") || args[2].equals("true");

            Groups.player.each(p -> p.uuid().equals(target.id) || p.ip().equals(target.lastIP), p -> p.kick(Packets.KickReason.banned, 0));

            if (global) {
                IPBanData ban = IPBanData.builder()
                        .ip(target.lastIP)
                        .name(target.lastName)
                        .adminName("console")
                        .reason(args[3])
                        .unbanDate(unbanDate)
                        .build();

                JavelinCommunicator.sendEvent(ban);
                database.getBanDataExecutor().saveIPBan(ban);
                Log.info("'@' (@) banned", ban.ip, target.lastName);
            } else {
                UUIDBanData ban = UUIDBanData.builder()
                        .uuid(target.id)
                        .name(target.lastName)
                        .adminName("console")
                        .reason(args[3])
                        .server(config.server)
                        .unbanDate(unbanDate)
                        .build();

                JavelinCommunicator.sendEvent(ban);
                database.getBanDataExecutor().saveUUIDBan(ban);
                Log.info("'@' (@) banned", ban.name, ban.uuid);
            }
        });
        handler.register("tempbans", "List all temporary banned players.", args -> {
            Log.info("Temporary banned players:");
            Seq<UUIDBanData> uuidBans = database.getBanDataExecutor().getUUIDBanned();
            Seq<IPBanData> ipBans = database.getBanDataExecutor().getIPBanned();

            Log.info("Temporary UUID banned players:");
            uuidBans.each(ban -> {
                var date = LocalDateTime.ofInstant(Instant.ofEpochMilli(ban.unbanDate), ZoneId.systemDefault()).toString();
                Log.info("'@' / Name: @ / Admin: @ / Unban date: @ / Reason: '@'", ban.uuid, ban.name, ban.adminName, date, ban.reason);
            });
            Log.info("Temporary IP banned players:");
            ipBans.each(ban -> {
                var date = LocalDateTime.ofInstant(Instant.ofEpochMilli(ban.unbanDate), ZoneId.systemDefault()).toString();
                Log.info("'@' / Name: @ / Admin: @ / Unban date: @ / Reason: '@'", ban.ip, ban.name, ban.adminName, date, ban.reason);
            });
        });

        handler.register("tempunban", "<name/uuid/ip>", "Unban a temporary banned player.", args -> {
            var info = Find.playerInfo(args[0]);

            if (info == null) {
                Log.err("Player not found.");
                return;
            }

            database.getBanDataExecutor().deleteUUIDBan(info.id);
            database.getBanDataExecutor().deleteIPBan(args[0]);
            Log.info("Unbanned @", info.lastName);
        });

        handler.register("mute", "<player> <period>", "shut up", (args) -> {
            var target = Find.playerInfo(args[0]);

            if (target == null) {
                Log.err("Player not found.");
                return;
            }

            PlayerData data = database.getCached(target.id);
            if (data == null) {
                data = database.getPlayerDataExecutor().getPlayerData(target.id);
            }

            data.muted = Time.millis() + TimeUnit.HOURS.toMillis(Strings.parseInt(args[1]));

            database.getPlayerDataExecutor().setPlayerData(data);
            Log.info("@ (@) muted for @ hours", target.lastName, target.id, args[1]);
        });

        handler.register("unmute", "<player>", (args, player) -> {
            var target = Find.playerInfo(args[0]);

            if (target == null) {
                Log.err("Player not found.");
                return;
            }

            PlayerData data = database.getCached(target.id);
            if (data == null) {
                data = database.getPlayerDataExecutor().getPlayerData(target.id);
            }

            data.muted = 0;
            database.getPlayerDataExecutor().setPlayerData(data);
            Log.info("@ unmuted", target.lastName);
        });
    }
}
