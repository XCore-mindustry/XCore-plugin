package org.xcore.plugin.listeners;

import arc.Events;
import arc.func.Boolf;
import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import arc.util.Time;
import lombok.Getter;
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
import org.xcore.plugin.modules.Translator;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.models.*;

import java.time.Duration;

import static arc.util.Strings.stripColors;
import static com.ospx.flubundle.Bundle.args;
import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.utils.Utils.voteChoice;
public class NetEvents {
    public static final Seq<String> bannedNames = Seq.with("valve", "tuttop", "codex", "igggames", "igg-games.com", "igruhaorg", "freetp.org", "goldberg", "rog");
    @Getter
    public static Boolf<String> ipAcceptor = (ip) -> true;
    public static int blockedIPs = 0;
    public static int blockedIPsPerMinute = 0;

    public static String chat(Player author, String text) {
        int sign = voteChoice(text);
        if (sign != 0 && vote != null) {
            if (vote.voted.containsKey(author.id)) {
                bundle.send(author, "error-already-voted", args());
                return null;
            }
            vote.vote(author, sign);
        }

        Log.info("&fi@: @", "&lc" + author.plainName(), "&lw" + text);

        MuteData mute = database.getMuteDatas().get(author.uuid());

        if(mute != null) {
            if(!mute.expired()){
                Duration remain = Duration.ofMillis(mute.expireDate.getTime() - Time.millis());

                bundle.send(player, "you-are-muted",
                    args("adminName", mute.adminName,
                        "reason", mute.reason,
                        "remainMinutes", remain.toMinutes(),
                        "remainSeconds", remain.toSecondsPart()
                    )
                );
                return null;
            }

            database.getMuteDatas().delete(player.uuid());
        }

        author.sendMessage(netServer.chatFormatter.format(author, text), author, text);
        Translator.translate(author, text);

        NetSock.post(new SocketEvents.MessageEvent(author.plainName(), text.replace("`", "*"), config.server));
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
                bundle.send("tempban-player-banned", args(
                        "adminName", admin.coloredName(),
                        "playerName", target.coloredName()));
                Log.info("@ banned @ (@)", admin.plainName(), target.plainName(), target.uuid());

                var targetData = database.getCached(target.uuid());
                String banJson = rawGson.toJson(new BanRequestData(targetData.pid, target.coloredName()));

                Call.clientPacketReliable(admin.con, "give_ban_data", banJson);
            }
            case trace -> {
                var data = database.getCachedOrDb(target.uuid());

                var trace = new TraceInfo(
                        target.ip(),
                        String.valueOf(data == null ? -1 : data.pid),
                        target.locale(),
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

            case switchTeam -> bundle.send(player, "error-access-denied", args());
        }
    }

    public static boolean connectFilter(String address) {
        if (!ipAcceptor.get(address)) {
            blockedIPs++;
            blockedIPsPerMinute++;
            return false;
        }

        return true;
    }

    public static void connect(NetConnection con, Packets.Connect packet) {
        Events.fire(new EventType.ConnectionEvent(con));

        var connections = Seq.with(net.getConnections()).select(connection -> connection.address.equals(con.address));
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

        if (bannedNames.contains(packet.name.toLowerCase())) {
            con.kick(bundle.format(bundle.locale(packet.locale), "kick-pirated-game", args()), 0);
            return;
        }


        BanData ban = database.getBanDatas().get(uuid, con.address);
        if (ban != null) {
            if (ban.expired()) {
                netServer.admins.unbanPlayerID(uuid);
                netServer.admins.unbanPlayerIP(con.address);
                database.getBanDatas().delete(ban.uuid, con.address);
            } else {
                tempBanKick(con, packet.locale, ban);
                return;
            }
        }

        if (
            netServer.admins.isIPBanned(con.address) ||
            netServer.admins.isSubnetBanned(con.address) ||
            netServer.admins.isIDBanned(uuid)
        ) {
            con.kick(
                bundle.format(bundle.locale(packet.locale),
                    "ban-content", args(
                        "nickname", stripColors(packet.name),
                        "discordUrl", discordUrl)
                ),
                0
            );
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

        long kickTime = netServer.admins.getKickTime(uuid, con.address);
        if (Time.millis() < kickTime) {
            Duration remain = Duration.ofMillis(kickTime - Time.millis());
            con.kick(
                bundle.format(bundle.locale(packet.locale),
                    "kick-recently-kicked", args(
                        "remainMinutes", remain.toMinutes(),
                        "remainSeconds", remain.toSecondsPart())
                ),
                0
            );
            return;
        }
        if (!netServer.admins.isAdmin(uuid, packet.usid) && config.playerLimit > 0 && Groups.player.size() >= config.getNoAdminPlayerLimit()) {
            con.kick(Packets.KickReason.playerLimit);
            return;
        }

        Seq<String> extraMods = packet.mods.copy();
        Seq<String> missingMods = mods.getIncompatibility(extraMods);

        if (!extraMods.isEmpty() || !missingMods.isEmpty()) {

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

        if (packet.name.trim().isEmpty()) {
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
        Duration duration = Duration.ofMillis(ban.expireDate.getTime() - Time.millis());

        con.kick(bundle.format(bundle.locale(locale), "tempban-content", args(
                "nickname", stripColors(ban.name),
                "adminName", stripColors(ban.adminName),
                "reason", ban.reason,
                "days", duration.toDays(),
                "hours", duration.toHoursPart(),
                "minutes", duration.toMinutesPart(),
                "discordUrl", discordUrl)
        ), 0);
    }

    public static void setIpAcceptor(Boolf<String> ipAcceptor) {
        NetEvents.ipAcceptor = ipAcceptor;
    }
}
