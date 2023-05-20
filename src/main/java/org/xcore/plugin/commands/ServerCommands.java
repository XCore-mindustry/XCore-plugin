package org.xcore.plugin.commands;

import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
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

        handler.register("tempban", "<uuid/ip/#id> <period> [reason...]", "Temporary ban player.", args -> {
            var target = Find.playerInfo(args[0]);

            String name = target != null ? target.lastName : "Unknown";
            String uuid = target != null ? target.id : null;
            String ip = target != null ? target.lastIP : null;

            if (target == null && !args[0].startsWith("#")) {
                Log.err("Player not found");
                return;
            }

            if (args[0].startsWith("#")) {
                var data = database.getPlayerDataExecutor().getPlayerDataById(Strings.parseInt(args[0].substring(1)));

                if (data == null) {
                    Log.err("Player not found");
                    return;
                }

                name = data.nickname;
                uuid = data.uuid;
                var info = netServer.admins.getInfoOptional(uuid);
                ip = info != null ? info.lastIP : null;
            }

            Instant date = Utils.parsePeriod(args[1], TimeUnit.DAYS);

            if (date == null) {
                Log.err("Invalid period format. Example: 1h30m, 30 (hours)");
                return;
            }

            Date unbanDate = new Date(Time.millis() + date.toEpochMilli());
            SockCommunicator.sendEvent(new SocketEvents.KickBannedPlayer(uuid, ip));

            BanData result = BanData.builder()
                    .name(name)
                    .uuid(uuid)
                    .ip(ip)
                    .adminName("console")
                    .reason(args.length > 2 ? args[2] : "Not Specified")
                    .unbanDate(unbanDate)
                    .build();

            SockCommunicator.sendEvent(result);
            database.getBanDataExecutor().saveBan(result);
            Log.info("'@' (@/@) banned", result.name, result.uuid, result.ip);
        });

        handler.register("tempbans", "List all temporarily banned players.", args -> {
            StringBuilder builder = new StringBuilder("Temporary banned players:");
            Seq<BanData> bans = database.getBanDataExecutor().getBanned();

            bans.each(ban -> builder.append(Strings.format("\n'@/@' / Name: @ / Admin: @ / Unban date: @ / Reason: '@'",
                    ban.uuid, ban.ip, ban.name, ban.adminName,
                    ban.unbanDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    ban.reason)));

            Log.info(builder.toString());
        });


        handler.register("tempunban", "<uuid/ip>", "Unban a temporarily banned player.", args -> {
            var info = netServer.admins.getInfoOptional(args[0]);
            String uuid;
            String ip;

            if (info == null) {
                uuid = args[0];
                ip = null;
                Log.info("Info not found, unbanning only by UUID (@)", uuid);
            } else if (args[0].startsWith("#")) {
                uuid = info.id;
                ip = args[0].substring(1);
                Log.info("Unbanning by IP and internal UUID (@, @)", ip, uuid);
            } else {
                uuid = args[0];
                ip = info.lastIP;
                Log.info("Info found, unbanning by provided UUID and internal IP (@, @)", uuid, ip);
            }

            database.getBanDataExecutor().deleteBan(uuid, ip);
        });


        handler.register("mute", "<player> <period>", "Mute player", args -> {
            var info = Find.playerInfo(args[0]);

            if (info == null) {
                Log.err("Player not found.");
                return;
            }

            PlayerData data = database.getCachedOrDb(info.id);
            Instant date = Utils.parsePeriod(args[1], TimeUnit.HOURS);

            if (date == null) {
                Log.err("Invalid period format. Example: 1h30m, 30 (hours)");
                return;
            }

            data.muted = Time.millis() + date.toEpochMilli();

            database.getPlayerDataExecutor().setPlayerData(data);
            Duration duration = Duration.ofMillis(date.toEpochMilli());
            Log.info("@ (@) muted for @:@", info.lastName, info.id, duration.toMinutes(), duration.toSecondsPart());
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
