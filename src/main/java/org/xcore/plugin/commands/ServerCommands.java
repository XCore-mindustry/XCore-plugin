package org.xcore.plugin.commands;

import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.CommandHandler;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.JsonReader;
import arc.util.serialization.JsonValue;
import arc.util.serialization.JsonWriter;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration.PlayerInfo;
import mindustry.net.Packets;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.Config;
import org.xcore.plugin.modules.GlobalConfig;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.TextArgumentSplitter;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.MuteData;
import org.xcore.plugin.utils.models.PlayerData;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.utils.Utils.equalsHasNull;
import static org.xcore.plugin.utils.Utils.deepEquals;
import static org.xcore.plugin.PluginVars.*;

public class ServerCommands {
    public static void register(CommandHandler handler) {
        handler.removeCommand("exit");
        handler.register("exit", "Exit the server application.", args -> {
            Log.info("Shutting down server.");
            netServer.kickAll(Packets.KickReason.serverRestarting);
            System.exit(0);
        });
        handler.register("db-clear-bots", "Clear all bots from database, ONLY OSPX CAN USE!", args -> {
            long deleted = database.getPlayerDatas().clearBots();
            NetSock.post(new SocketEvents.ReloadPlayerDataCache());

            Log.info("Deleted @ bots", deleted);
        });

        handler.removeCommand("info");
        handler.register("info", "<IP/UUID/#id/name...>", "Find player info(s).", args -> {
            ObjectSet<PlayerInfo> infos;
            String query = args[0];

            if (query.startsWith("#")) {
                int id = Strings.parseInt(query.substring(1));
                PlayerData data = database.getCachedOrDb(id);
                infos = (data != null) ? ObjectSet.with(netServer.admins.getInfoOptional(data.uuid)) : new ObjectSet<>();
            } else {
                infos = netServer.admins.findByName(query);
            }

            if (infos.isEmpty()) {
                Log.info("Nobody with that name could be found.");
                return;
            }

            Log.info("Players found: @", infos.size);
            int index = 0;

            for (PlayerInfo info : infos) {
                Log.info("[@] Trace info for player '@' / UUID @ / RAW @", index, info.plainLastName(), info.id, info.lastName);
                Log.info("  all names used: @", info.names);
                Log.info("  IP: @", info.lastIP);
                Log.info("  all IPs used: @", info.ips);
                Log.info("  times joined: @", info.timesJoined);
                Log.info("  times kicked: @", info.timesKicked);
                index++;
            }
        });
        handler.removeCommand("players");
        handler.register("players", "List all players currently in game.", args -> {
            if (Groups.player.size() == 0) {
                Log.info("No players are currently in the server.");
            } else {
                Log.info("Players: @", Groups.player.size());
                for (Player user : Groups.player) {
                    PlayerInfo userInfo = user.getInfo();
                    PlayerData data = database.getCached(user.uuid());
                    Log.info(" @&lm @ / UUID: @/@ / IP: @", userInfo.admin ? "&r[A]&c" : "&b[P]&c", userInfo.plainLastName(), data.pid, userInfo.id, userInfo.lastIP, userInfo.admin);
                }
            }
        });

        handler.register("reload-config", "Reload config", args -> {
            Config.init();
            GlobalConfig.init();
        });

        handler.register("xconfig", "[field] [value]", "Configure xcore plugin", args -> {
            String json = prettyGson.toJson(config);

            if (args.length == 0) {
                Log.info(prettyGson.toJson(config));
                return;
            }

            if (args.length < 2) {
                Log.err("Missing 2 arguments.");
                return;
            }

            String field = args[0];
            String value = args[1];
            
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

            Config result = prettyGson.fromJson(reader.toJson(JsonWriter.OutputType.json), Config.class);
            configFile.writeString(prettyGson.toJson(config = result));

            Log.info("Done.");
        });

        handler.register("edit-data", "<#id/uuid> <perm> <value>", "Give/remove permission.", args -> {
            PlayerData data = Find.playerData(args[0]);
            String field = args[1];
            String value = args[2];

            if (data == null) {
                Log.info("Player not found.");
                return;
            }

            String json = prettyGson.toJson(data);
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

            PlayerData result = prettyGson.fromJson(reader.toJson(JsonWriter.OutputType.json), PlayerData.class);
            result.save();
            Log.info("Done.");
        });

        handler.register("dbinfo", "<#id/uuid>", "Info about player from db.", args -> {
            PlayerData data = Find.playerData(args[0]);

            if (data == null) {
                Log.err("Player not found");
                return;
            }

            Log.info(prettyGson.toJson(data));
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
                var data = database.getPlayerDatas().getById(Strings.parseInt(args[0].substring(1)));

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
                Log.err("Invalid period format. Example: 1h30m, 30 (days)");
                return;
            }

            Date unbanDate = new Date(Time.millis() + date.toEpochMilli());
            NetSock.post(new SocketEvents.KickBannedPlayer(uuid, ip));

            BanData result = BanData.builder()
                    .name(name)
                    .uuid(uuid)
                    .ip(ip)
                    .adminName("console")
                    .reason(args.length > 2 ? args[2] : "Not Specified")
                    .expireDate(unbanDate)
                    .build();

            NetSock.post(result);
            result.save();
            Log.info("'@' (@/@) banned", result.name, result.uuid, result.ip);
        });

        handler.register("tempbans", "[search...]", "List all temporarily banned players.", args -> {
            StringBuilder builder = new StringBuilder("Temporary banned players:");
            Seq<BanData> bans = database.getBanDatas().getPunished();

            if (args.length > 0) {
                bans.filter(b -> deepEquals(b.name, args[0]) || equalsHasNull(b.ip, args[0]) || equalsHasNull(b.uuid, args[0]));
            }

            bans.each(ban -> builder.append(Strings.format("\n'@/@' / Name: @ / Admin: @ / Unban date: @ / Reason: '@'".replace("@", "&fb&lb@&fr"),
                    ban.uuid, ban.ip, ban.name, ban.adminName,
                    ban.expireDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    ban.reason)));

            Log.info(builder.toString());
        });


        handler.register("tempunban", "<type:uuid/ip/id> <value>", "Unban a temporarily banned player.", args -> {
            String uuid = null;
            String ip = null;
            switch (args[0]) {
                case "uuid", "uid" -> uuid = args[1];

                case "ip" -> ip = args[1];

                case "id" -> {
                    PlayerData data = database.getCachedOrDb(Strings.parseInt(args[1]));

                    if (data == null) {
                        Log.err("Player not found");
                        return;
                    }

                    uuid = data.uuid;
                }

                default -> Log.err("Incorrect type. Example: uuid/ip/id");
            }

            database.getBanDatas().delete(uuid, ip);
            Log.info("Unbanned (@/@)", uuid, ip);
        });


        handler.register("mute", "<uuid/#id> <period> [reason...]", "Mute player", args -> {
            PlayerData data = Find.playerData(args[0]);

            if (data == null) {
                Log.err("Player not found.");
                return;
            }

            Instant date = Utils.parsePeriod(args[1], TimeUnit.HOURS);

            if (date == null) {
                Log.err("Invalid period format. Example: 1h30m, 30 (hours)");
                return;
            }

            var muteData = MuteData.builder()
                    .uuid(data.uuid)
                    .name(data.nickname)
                    .adminName("console")
                    .reason(args.length > 2 ? args[2] : "Not Specified")
                    .expireDate(new Date(Time.millis() + date.toEpochMilli()))
                    .build();
            database.muteDatas.save(muteData);
            NetSock.post(muteData);

            Duration duration = Duration.ofMillis(date.toEpochMilli());
            Log.info("@ (@) muted for @:@", data.nickname, data.uuid, duration.toMinutes(), duration.toSecondsPart());
        });


        handler.register("unmute", "<uuid/#id>", "Unmute player", (args, player) -> {
            PlayerData data = Find.playerData(args[0]);

            if (data == null) {
                Log.err("Player not found.");
                return;
            }
            database.muteDatas.delete(data.uuid);
            data.save();
            Log.info("@ (@) unmuted", data.nickname, data.uuid);
        });

        handler.register("gcmd", "<args...>", "Execute command on all servers", args -> {
            var argsQuotes = TextArgumentSplitter.split(args[0]);
            var expectServers = new Seq<String>();

            if (argsQuotes.length == 0) {
                Log.err("Missing arguments.");
                return;
            }

            if (argsQuotes[0].startsWith("gcmd")) {
                Log.err("Recursive command.");
                return;
            }

            if (argsQuotes.length > 1) {
                for (int i = 1; i < argsQuotes.length; i++) {
                    expectServers.add(argsQuotes[i]);
                }
            }

            NetSock.post(new SocketEvents.ExecuteCommand(argsQuotes[0], expectServers.toArray(String.class)));
            Log.info("Done");
        });

        handler.register("sock-restart", "Restart sock", args -> {
            NetSock.sock.disconnect();
            NetSock.safeConnect();
            Log.info("Done");
        });

        handler.register("gg-restart", "[on/off]", "Restart the server on GameOver", args -> {
            gameoverRestart = args.length == 0 || !args[0].equals("off");

            Log.info("GameOver restart turned @", gameoverRestart ? "on" : "off");
        });
    }
}
