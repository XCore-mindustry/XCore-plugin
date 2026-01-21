package org.xcore.plugin.commands.controllers.server;

import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.ServerContext;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.*;
import org.xcore.plugin.utils.models.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.PluginVars.database;
import static org.xcore.plugin.utils.Utils.*;

@SuppressWarnings("unused")
public class ModerationController {

    @Command(name = "tempban", params = "<uuid/ip/#id> <period> [reason...]", description = "Temp-ban a player.")
    public void tempBan(ServerContext ctx) {
        var target = Find.playerInfo(ctx.arg(0));
        String uuid = (target != null) ? target.id : (ctx.arg(0).startsWith("#") ? null : ctx.arg(0));
        String ip = (target != null) ? target.lastIP : null;
        String name = (target != null) ? target.lastName : "Unknown";

        if (ctx.arg(0).startsWith("#")) {
            var data = database.getPlayerDatas().getById(Strings.parseInt(ctx.arg(0).substring(1)));
            if (data == null) { Log.err("Player not found"); return; }
            uuid = data.uuid; name = data.nickname;
            var info = netServer.admins.getInfoOptional(uuid);
            if (info != null) ip = info.lastIP;
        }

        Instant period = Utils.parsePeriod(ctx.arg(1), TimeUnit.DAYS);
        if (period == null) { Log.err("Invalid period format."); return; }

        Instant expire = Instant.now().plusMillis(period.toEpochMilli());
        NetSock.post(new SocketEvents.KickBannedPlayer(uuid, ip));

        BanData ban = BanData.builder()
                .name(name).uuid(uuid).ip(ip).adminName("console")
                .reason(ctx.args().length > 2 ? ctx.arg(2) : "Not Specified")
                .expireDate(expire).build();

        NetSock.post(ban);
        ban.save();
        Log.info("Banned @ until @", name, expire);
    }

    @Command(name = "tempunban", params = "<type:uuid/ip/id> <value>", description = "Unban player.")
    public void tempUnban(ServerContext ctx) {
        String uuid = null, ip = null;
        switch (ctx.arg(0).toLowerCase()) {
            case "uuid", "uid" -> uuid = ctx.arg(1);
            case "ip" -> ip = ctx.arg(1);
            case "id" -> {
                var d = database.getCachedOrDb(Strings.parseInt(ctx.arg(1)));
                if (d != null) uuid = d.uuid;
            }
        }
        database.getBanDatas().delete(uuid, ip);
        Log.info("Unbanned: UUID=@ / IP=@", uuid, ip);
    }

    @Command(name = "tempbans", params = "[search...]", description = "List current temp bans.")
    public void tempBans(ServerContext ctx) {
        Seq<BanData> bans = database.getBanDatas().getPunished();
        if (ctx.args().length > 0) {
            String q = ctx.arg(0);
            bans.select(b -> deepEquals(b.name, q) || equalsHasNull(b.ip, q) || equalsHasNull(b.uuid, q));
        }
        bans.each(b -> Log.info("Ban: @ (@) until @. Reason: @", b.name, b.uuid, b.expireDate.atZone(ZoneId.systemDefault()).toLocalDateTime(), b.reason));
    }

    @Command(name = "mute", params = "<uuid/#id> <period> [reason...]", description = "Mute player.")
    public void mute(ServerContext ctx) {
        PlayerData data = Find.playerData(ctx.arg(0));
        if (data == null) return;
        Instant period = Utils.parsePeriod(ctx.arg(1), TimeUnit.HOURS);
        if (period == null) return;

        MuteData m = MuteData.builder().uuid(data.uuid).name(data.nickname).adminName("console")
                .reason(ctx.args().length > 2 ? ctx.arg(2) : "Not Specified")
                .expireDate(Instant.now().plusMillis(period.toEpochMilli())).build();
        database.muteDatas.save(m);
        NetSock.post(m);
        Log.info("Muted @ for @ minutes.", data.nickname, Duration.ofMillis(period.toEpochMilli()).toMinutes());
    }

    @Command(name = "unmute", params = "<uuid/#id>", description = "Unmute player.")
    public void unmute(ServerContext ctx) {
        PlayerData data = Find.playerData(ctx.arg(0));
        if (data != null) database.muteDatas.delete(data.uuid);
        Log.info("Unmuted @", data != null ? data.nickname : "unknown");
    }
}