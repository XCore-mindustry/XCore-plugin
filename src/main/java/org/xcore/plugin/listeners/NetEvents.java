package org.xcore.plugin.listeners;

import arc.Events;
import arc.graphics.Color;
import arc.graphics.Colors;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import arc.util.serialization.Base64Coder;
import fr.xpdustry.javelin.JavelinPlugin;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.xcore.plugin.PluginVars;
import org.xcore.plugin.modules.Translator;
import org.xcore.plugin.modules.discord.Bot;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.zip.CRC32;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.PluginVars.isSocketServer;

public class NetEvents {
    public static String chat(Player author, String text) {
        Log.info("&fi@: @", "&lc" + author.plainName(), "&lw" + text);

        author.sendMessage(netServer.chatFormatter.format(author, text), author, text);
        Translator.translate(author, text);

        if (isSocketServer) {
            Bot.sendMessageEventMessage(author.plainName(), text);
        } else {
            JavelinPlugin.getJavelinSocket().sendEvent(
                    new SocketEvents.MessageEvent(author.plainName(), text, config.server));
        }
        return null;

        //var data = Database.cachedPlayerData.get(player.uuid());
        //return "[coral][[[cyan]" + (config.isMiniPvP() ? data.pvpRating : data.hexedWins) + " [sky]#[white] " + player.coloredName() + "[coral]]: [white]" + message;
    }

    public static void adminRequest(NetConnection con, AdminRequestCallPacket packet) {
        Player admin = con.player, target = packet.other;
        var action = packet.action;

        if (!admin.admin || target == null || (target.admin && target != admin)) return;

        Events.fire(new EventType.AdminRequestEvent(admin, target, action));

        switch (action) {
            case kick -> target.kick(Packets.KickReason.kick);
            case ban -> {
                target.kick(Packets.KickReason.banned);
                netServer.admins.banPlayerID(target.uuid());
                netServer.admins.banPlayerIP(target.ip());
                Call.sendMessage(Strings.format("@[] banned @[].", admin.coloredName(), target.coloredName()));

                if (isSocketServer) {
                    Bot.sendBanEvent(target.plainName(), admin.plainName());
                } else {
                    JavelinPlugin.getJavelinSocket().sendEvent(new SocketEvents.BanEvent(target.plainName(), admin.plainName(), config.server));
                }
            }
            case trace -> {
                var info = target.getInfo();
                Call.traceInfo(con, target, new Administration.TraceInfo(target.ip(), target.uuid(), target.con.modclient, target.con.mobile, info.timesJoined, info.timesKicked));
                Log.info("@ has requested trace info of @.", admin.plainName(), target.plainName());
            }
            case wave -> {
                logic.skipWave();
                Log.info("@ has skipped the wave.", admin.plainName());
            }
        }
    }

    public static void connect(NetConnection con, Packets.Connect packet) {
        Events.fire(new EventType.ConnectionEvent(con));

        var connections = Seq.with(net.getConnections()).filter(connection -> connection.address.equals(con.address));
        if (connections.size >= 3) {
            netServer.admins.blacklistDos(con.address);
            connections.each(NetConnection::close);
        }
    }

    public static void connectPacket(NetConnection con, Packets.ConnectPacket packet) {
        if (con.kicked) return;

        Events.fire(new EventType.ConnectPacketEvent(con, packet));

        con.connectTime = Time.millis();

        String uuid = packet.uuid;
        byte[] buuid = Base64Coder.decode(uuid);
        CRC32 crc = new CRC32();
        crc.update(buuid, 0, 8);
        ByteBuffer buff = ByteBuffer.allocate(8);
        buff.put(buuid, 8, 8);
        if (crc.getValue() != buff.getLong(0)) {
            con.kick(Packets.KickReason.clientOutdated);
            return;
        }

        String banReason = """
                [accent]You are banned from this server.
                If you want to apply for an unban, please join our discord server and write appeal in the #appeals channel.
                                
                [blue]Discord:[cyan]
                """ + PluginVars.discordURL;

        if (netServer.admins.isIPBanned(con.address) || netServer.admins.isSubnetBanned(con.address)) {
            con.kick(banReason);
            return;
        }

        if (con.hasBegunConnecting) {
            con.kick(Packets.KickReason.idInUse);
            return;
        }

        Administration.PlayerInfo info = netServer.admins.getInfo(uuid);

        con.hasBegunConnecting = true;
        con.mobile = packet.mobile;

        if (packet.uuid == null || packet.usid == null) {
            con.kick(Packets.KickReason.idInUse);
            return;
        }

        if (netServer.admins.isIDBanned(uuid)) {
            con.kick(banReason);
            return;
        }

        long kickTime = netServer.admins.getKickTime(uuid, con.address);
        if (Time.millis() < kickTime) {
            Duration remain = Duration.ofMillis(kickTime - Time.millis());
            con.kick(Strings.format("[accent]You were recently kicked from this server. Wait [cyan]@:@[accent].", remain.toMinutes(), remain.toSecondsPart()));
            return;
        }

        if (netServer.admins.getPlayerLimit() > 0 && Groups.player.size() >= netServer.admins.getPlayerLimit() && !netServer.admins.isAdmin(uuid, packet.usid)) {
            con.kick(Packets.KickReason.playerLimit);
            return;
        }

        Seq<String> extraMods = packet.mods.copy();
        Seq<String> missingMods = mods.getIncompatibility(extraMods);

        if (!extraMods.isEmpty() || !missingMods.isEmpty()) {
            //can't easily be localized since kick reasons can't have formatted text with them
            StringBuilder result = new StringBuilder("[accent]Incompatible mods![]\n\n");
            if (!missingMods.isEmpty()) {
                result.append("Missing:[lightgray]\n").append("> ").append(missingMods.toString("\n> "));
                result.append("[]\n");
            }

            if (!extraMods.isEmpty()) {
                result.append("Unnecessary mods:[lightgray]\n").append("> ").append(extraMods.toString("\n> "));
            }
            con.kick(result.toString(), 0);
        }

        if (!netServer.admins.isWhitelisted(packet.uuid, packet.usid)) {
            info.adminUsid = packet.usid;
            info.lastName = packet.name;
            info.id = packet.uuid;
            netServer.admins.save();
            Call.infoMessage(con, "You are not whitelisted here.");
            Log.info("&lcDo &lywhitelist-add @&lc to whitelist the player &lb'@'", packet.uuid, packet.name);
            con.kick(Packets.KickReason.whitelist);
            return;
        }

        if (packet.versionType == null || ((packet.version == -1 || !packet.versionType.equals(Version.type)) && Version.build != -1 && !netServer.admins.allowsCustomClients())) {
            con.kick(!Version.type.equals(packet.versionType) ? Packets.KickReason.typeMismatch : Packets.KickReason.customClient);
            return;
        }

        boolean preventDuplicates = headless && netServer.admins.isStrict();

        if (preventDuplicates) {
            if (Groups.player.contains(p -> Strings.stripColors(p.name).trim().equalsIgnoreCase(Strings.stripColors(packet.name).trim()))) {
                con.kick(Packets.KickReason.nameInUse);
                return;
            }

            if (Groups.player.contains(player -> player.uuid().equals(packet.uuid) || player.usid().equals(packet.usid))) {
                con.uuid = packet.uuid;
                con.kick(Packets.KickReason.idInUse);
                return;
            }

            for (var otherCon : net.getConnections()) {
                if (otherCon != con && uuid.equals(otherCon.uuid)) {
                    con.uuid = packet.uuid;
                    con.kick(Packets.KickReason.idInUse);
                    return;
                }
            }
        }

        packet.name = fixName(packet.name);

        if (packet.name.trim().length() <= 0) {
            con.kick(Packets.KickReason.nameEmpty);
            return;
        }

        if (packet.locale == null) {
            packet.locale = "en";
        }

        String ip = con.address;

        netServer.admins.updatePlayerJoined(uuid, ip, packet.name);

        if (packet.version != Version.build && Version.build != -1 && packet.version != -1) {
            con.kick(packet.version > Version.build ? Packets.KickReason.serverOutdated : Packets.KickReason.clientOutdated);
            return;
        }

        if (packet.version == -1) {
            con.modclient = true;
        }

        Player player = Player.create();
        player.admin = netServer.admins.isAdmin(uuid, packet.usid);
        player.con = con;
        player.con.usid = packet.usid;
        player.con.uuid = uuid;
        player.con.mobile = packet.mobile;
        player.name = packet.name;
        player.locale = packet.locale;
        player.color.set(packet.color).a(1f);

        //save admin ID but don't overwrite it
        if (!player.admin && !info.admin) {
            info.adminUsid = packet.usid;
        }

        con.player = player;

        //playing in pvp mode automatically assigns players to teams
        player.team(netServer.assignTeam(player));

        netServer.sendWorldData(player);

        Events.fire(new EventType.PlayerConnect(player));
    }

    public static String fixName(String name) {
        name = name.trim().replace("\n", "").replace("\t", "");
        if (name.equals("[") || name.equals("]")) {
            return "";
        }

        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == '[' && i != name.length() - 1 && name.charAt(i + 1) != '[' && (i == 0 || name.charAt(i - 1) != '[')) {
                String prev = name.substring(0, i);
                String next = name.substring(i);
                String result = checkColor(next);

                name = prev + result;
            }
        }

        StringBuilder result = new StringBuilder();
        int curChar = 0;
        while (curChar < name.length() && result.toString().getBytes(Strings.utf8).length < maxNameLength) {
            result.append(name.charAt(curChar++));
        }
        return result.toString();
    }

    public static String checkColor(String str) {
        for (int i = 1; i < str.length(); i++) {
            if (str.charAt(i) == ']') {
                String color = str.substring(1, i);

                if (Colors.get(color.toUpperCase()) != null || Colors.get(color.toLowerCase()) != null) {
                    Color result = (Colors.get(color.toLowerCase()) == null ? Colors.get(color.toUpperCase()) : Colors.get(color.toLowerCase()));
                    if (result.a < 1f) {
                        return str.substring(i + 1);
                    }
                } else {
                    try {
                        Color result = Color.valueOf(color);
                        if (result.a < 1f) {
                            return str.substring(i + 1);
                        }
                    } catch (Exception e) {
                        return str;
                    }
                }
            }
        }
        return str;
    }
}
