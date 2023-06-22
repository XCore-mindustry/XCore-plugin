package org.xcore.plugin.listeners;

import arc.Events;
import arc.func.Boolf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Administration.TraceInfo;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.json.JSONObject;
import org.xcore.plugin.modules.Translator;
import org.xcore.plugin.utils.SockCommunicator;
import org.xcore.plugin.utils.models.BanData;

import java.time.Duration;

import static arc.util.Strings.stripColors;
import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.utils.Utils.voteChoice;
import static useful.Bundle.format;
import static useful.Bundle.send;

public class NetEvents {
    public static final Seq<String> bannedNames = Seq.with("valve", "tuttop");
    public static Boolf<String> ipAcceptor = (ip) -> true;
    public static String chat(Player author, String text) {
        int sign = voteChoice(text);
        if (sign != 0 && vote != null) {
            if (vote.voted.containsKey(author.id)) {
                send(author, "error.already-voted");
                return null;
            }
            vote.vote(author, sign);
        }

        Log.info("&fi@: @", "&lc" + author.plainName(), "&lw" + text);

        var data = database.getCached(author.uuid());
        if (data.muted > Time.millis()) {
            Duration remain = Duration.ofMillis(data.muted - Time.millis());
            send(author, "you-are-muted", remain.toMinutes(), remain.toSecondsPart());
            return null;
        }

        author.sendMessage(netServer.chatFormatter.format(author, text), author, text);
        Translator.translate(author, text);

        SockCommunicator.sendEvent(new SocketEvents.MessageEvent(author.plainName(), text.replace("`", "*"), config.server));
        return null;
    }

    public static void adminRequest(NetConnection con, AdminRequestCallPacket packet) {
        Player admin = con.player, target = packet.other;
        var action = packet.action;

        if (!admin.admin || target == null || (target.admin && target != admin)) return;

        Events.fire(new EventType.AdminRequestEvent(admin, target, action));

        switch (action) {
            case kick -> {
                target.kick(Packets.KickReason.kick);
                Call.sendMessage(Strings.format("@[accent] kicked @[].", admin.coloredName(), target.coloredName()));
                Log.info("@ kicked @ (@)", admin.plainName(), target.plainName(), target.uuid());
            }
            case ban -> {
                target.kick(Packets.KickReason.banned);
                netServer.admins.banPlayerID(target.uuid());
                Call.sendMessage(Strings.format("@[accent] banned @[].", admin.coloredName(), target.coloredName()));
                Log.info("@ banned @ (@)", admin.plainName(), target.plainName(), target.uuid());

                String banJson = new JSONObject()
                        .put("name", target.name)
                        .put("uuid", target.uuid())
                        .put("ip", target.ip())
                        .put("reason", "")
                        .put("duration", "0")
                        .put("skip_to_discord", false)
                        .put("global", false)
                        .toString();

                Call.clientPacketReliable(admin.con, "give_ban_data", banJson);
            }
            case trace -> {
                var data = database.getCachedOrDb(target.uuid());

                if (data == null) {
                    Log.err("[trace] DB Data null");
                    return;
                }

                var trace = new TraceInfo(
                        target.ip(),
                        data.pid + "",
                        target.con.modclient,
                        target.con.mobile,
                        target.getInfo().timesJoined,
                        target.getInfo().timesKicked,
                        target.getInfo().ips.toArray(String.class),
                        target.getInfo().names.toArray(String.class)
                );

                Call.traceInfo(con, target, trace);
                Log.info("@ has requested trace info of @.", admin.plainName(), target.plainName());
            }
            case wave -> {
                logic.skipWave();
                Call.sendMessage(admin.name + "[accent] has skipped the wave.");
                Log.info("@ has skipped the wave.", admin.plainName());
            }

            case switchTeam -> {
                //if(packet.params instanceof Team team){
                //    target.team(team);
                //    Log.info("@ has switched team of @ to @", admin.plainName(), target.plainName(), team.name);
                //}
                send(player, "error.access-denied");
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

        if (!ipAcceptor.get(con.address)) {
            con.kick(packet.name + "[accent] subnet banned");
            return;
        }

        if (bannedNames.contains(packet.name.toLowerCase())) {
            con.kick(format("kick.pirated-game", packet.locale), 0);
            return;
        }


        BanData ban = database.getBanDataExecutor().getBan(uuid, con.address);
        if (ban != null) {
            if (ban.expired()) {
                netServer.admins.unbanPlayerID(uuid);
                netServer.admins.unbanPlayerIP(con.address);
                database.getBanDataExecutor().deleteBan(ban.uuid, con.address);
            } else {
                tempBanKick(con, packet.locale, ban);
                return;
            }
        }

        if (netServer.admins.isIPBanned(con.address) || netServer.admins.isSubnetBanned(con.address)) {
            con.kick(format("ban.content", packet.locale, stripColors(packet.name), discordUrl), 0);
            return;
        }

        if (con.hasBegunConnecting) {
            con.kick(Packets.KickReason.idInUse, 0);
            return;
        }

        Administration.PlayerInfo info = netServer.admins.getInfo(uuid);

        con.hasBegunConnecting = true;
        con.mobile = packet.mobile;

        if (packet.uuid == null || packet.usid == null) {
            con.kick(Packets.KickReason.idInUse, 0);
            return;
        }

        if (netServer.admins.isIDBanned(uuid)) {
            con.kick(format("ban.content", packet.locale, stripColors(packet.name), discordUrl), 0);
            return;
        }

        long kickTime = netServer.admins.getKickTime(uuid, con.address);
        if (Time.millis() < kickTime) {
            Duration remain = Duration.ofMillis(kickTime - Time.millis());
            con.kick(format("kick.recently-kicked", packet.locale, remain.toMinutes(), remain.toSecondsPart()), 0);
            return;
        }
        if (!netServer.admins.isAdmin(uuid, packet.usid) && config.playerLimit > 0 && Groups.player.size() >= config.getNoAdminPlayerLimit()) {
            con.kick(Packets.KickReason.playerLimit);
            return;
        }

        Seq<String> extraMods = packet.mods.copy();
        Seq<String> missingMods = mods.getIncompatibility(extraMods);

        if (!extraMods.isEmpty() || !missingMods.isEmpty()) {
            //can't easily be localized since kick reasons can't have a formatted text with them
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
            con.kick(Packets.KickReason.whitelist, 0);
            return;
        }

        if (packet.versionType == null || ((packet.version == -1 || !packet.versionType.equals(Version.type)) && Version.build != -1 && !netServer.admins.allowsCustomClients())) {
            con.kick(!Version.type.equals(packet.versionType) ? Packets.KickReason.typeMismatch : Packets.KickReason.customClient, 0);
            return;
        }

        boolean preventDuplicates = netServer.admins.isStrict();

        if (preventDuplicates) {
            if (Groups.player.contains(p -> stripColors(p.name).trim().equalsIgnoreCase(stripColors(packet.name).trim()))) {
                con.kick(Packets.KickReason.nameInUse, 0);
                return;
            }

            if (Groups.player.contains(player -> player.uuid().equals(packet.uuid) || player.usid().equals(packet.usid))) {
                con.uuid = packet.uuid;
                con.kick(Packets.KickReason.idInUse, 0);
                return;
            }

            for (var otherCon : net.getConnections()) {
                if (otherCon != con && uuid.equals(otherCon.uuid)) {
                    con.uuid = packet.uuid;
                    con.kick(Packets.KickReason.idInUse, 0);
                    return;
                }
            }
        }

        packet.name = netServer.fixName(packet.name);

        if (packet.name.trim().length() == 0) {
            con.kick(Packets.KickReason.nameEmpty);
            return;
        }

        if (packet.locale == null) {
            packet.locale = "en";
        }

        String ip = con.address;

        netServer.admins.updatePlayerJoined(uuid, ip, packet.name);

        if (packet.version != Version.build && Version.build != -1 && packet.version != -1) {
            con.kick(packet.version > Version.build ? Packets.KickReason.serverOutdated : Packets.KickReason.clientOutdated, 0);
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

    public static void tempBanKick(NetConnection con, String locale, BanData ban) {
        Duration duration = Duration.ofMillis(ban.unbanDate.getTime() - Time.millis());

        con.kick(format("tempban.content", locale,
                stripColors(ban.name),
                stripColors(ban.adminName),
                ban.reason,
                duration.toDays(), duration.toHoursPart(), duration.toMinutesPart(),
                discordUrl), 0);
    }

    public static Boolf<String> getIpAcceptor() {
        return ipAcceptor;
    }

    public static void setIpAcceptor(Boolf<String> ipAcceptor) {
        NetEvents.ipAcceptor = ipAcceptor;
    }
}