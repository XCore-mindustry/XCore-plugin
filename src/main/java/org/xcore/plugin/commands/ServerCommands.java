package org.xcore.plugin.commands;

import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Time;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import arc.util.serialization.JsonWriter;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.Packets;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.PlayerData;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static arc.Core.app;
import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.database;
import static org.xcore.plugin.PluginVars.gson;

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

            PlayerData data = database.getCachedOrDb(info.id);

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

            database.getPlayerDataExecutor().setPlayerData(result);
            Log.info("Done.");
        });

        handler.register("dbinfo", "<name/uuid/ip>", "Info about player from db.", args -> {
            PlayerInfo info = Find.playerInfo(args[0]);

            if (info == null) {
                Log.info("Player not found.");
                return;
            }

            PlayerData data = database.getCachedOrDb(args[0]);

            Log.info(gson.toJson(data));
        });

        handler.register("tempban", "<name/uuid/#ip> <days-of-ban> [reason...>]", "Temporary ban player.", args -> {
            var target = Find.playerInfo(args[0].startsWith("#") ? args[0].substring(1) : args[0]);

            String name = "Unknown";
            String uuid = null;
            String ip = null;
            if (target != null) {
                name = target.lastName;
                uuid = target.id;
                ip = target.lastIP;
            }

            if (args[0].startsWith("#")) {
                ip = args[0].substring(1);
            }

            Instant date = Utils.parsePeriod(args[1], TimeUnit.DAYS);
            Date unbanDate = new Date(Time.millis() + date.toEpochMilli());
            SockCommunicator.sendEvent(new SocketEvents.KickBannedPlayer(uuid, ip));

            BanData.BanDataBuilder ban = BanData.builder()
                    .name(name)
                    .uuid(uuid)
                    .ip(ip)
                    .adminName("console")
                    .reason(args.length > 2 ? args[2] : "Not Specified")
                    .unbanDate(unbanDate);

            BanData result = ban.build();

            SockCommunicator.sendEvent(result);
            database.getBanDataExecutor().saveBan(result);
            Log.info("'@' (@/@) banned", result.name, result.uuid, result.ip);
        });
        handler.register("tempbans", "List all temporary banned players.", args -> {
            Log.info("Temporary banned players:");
            Seq<BanData> bans = database.getBanDataExecutor().getBanned();

            bans.each(ban -> {
                var date = LocalDateTime.ofInstant(ban.unbanDate.toInstant(), ZoneId.systemDefault()).toString();
                Log.info("'@/@' / Name: @ / Admin: @ / Unban date: @ / Reason: '@'", ban.uuid, ban.ip, ban.name, ban.adminName, date, ban.reason);
            });
        });

        handler.register("tempunban", "<uuid/ip>", "Unban a temporary banned player.", args -> {
            var info = netServer.admins.getInfoOptional(args[0]);

            String uuid;
            String ip = null;
            if (args[0].startsWith("#")) {
                uuid = info == null ? null : info.id;
                ip = args[0].substring(1);
                Log.info("Unbanning by ip and internal uuid ()", ip, uuid);
            } else if (info != null) {
                uuid = args[0];
                ip = info.lastIP;
                Log.info("Info found, unbanning by provided uuid and internal ip (@, @)", uuid, ip);
            } else {
                uuid = args[0];
                Log.info("Info not found, Unbanning only by uuid (@)", uuid);
            }

            database.getBanDataExecutor().deleteBan(uuid, ip);
        });

        handler.register("mute", "<player> <period>", "Mute player", (args) -> {
            var target = Find.playerInfo(args[0]);

            if (target == null) {
                Log.err("Player not found.");
                return;
            }

            PlayerData data = database.getCachedOrDb(target.id);

            Instant date = Utils.parsePeriod(args[1], TimeUnit.HOURS);

            if (date == null) {
                Log.err("Invalid period format. Example: 1h30m, 30 (hours)");
                return;
            }

            data.muted = Time.millis() + date.toEpochMilli();

            database.getPlayerDataExecutor().setPlayerData(data);
            Duration duration = Duration.ofMillis(date.toEpochMilli());
            Log.info("@ (@) muted for @:@", target.lastName, target.id, duration.toMinutes(), duration.toSecondsPart());
        });

        handler.register("unmute", "<player>", "Unmute player", (args, player) -> {
            var target = Find.playerInfo(args[0]);

            if (target == null) {
                Log.err("Player not found.");
                return;
            }

            PlayerData data = database.getCachedOrDb(target.id);

            data.muted = 0;
            database.getPlayerDataExecutor().setPlayerData(data);
            Log.info("@ unmuted", target.lastName);
        });

        handler.register("sock-restart", "Restart sock", args -> {
            SockCommunicator.sock.disconnect();
            SockCommunicator.safeConnect();
            Log.info("Done");
        });
    }
}
