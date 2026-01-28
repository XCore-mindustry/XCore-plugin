package org.xcore.plugin.command.controller.server;

import arc.struct.Seq;
import arc.util.Log;
import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ServerContext;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.database.DatabaseService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.model.PlayerData;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;
import static org.xcore.plugin.common.TextUtils.deepEquals;
import static org.xcore.plugin.common.TextUtils.equalsNonEmpty;

@Singleton
public class ServerModerationController {

    private final DatabaseService database;
    private final NetworkService network;
    private final FindService find;
    private final TimeService time;

    @Inject
    public ServerModerationController(DatabaseService database, NetworkService network, FindService find,
                                      TimeService timeService) {
        this.database = database;
        this.network = network;
        this.find = find;
        this.time = timeService;
    }

    @Command(name = "tempban", params = "<uuid/ip/#id> <period> [reason...]", description = "Temp-ban a player.")
    public void tempBan(ServerContext ctx) {
        var target = find.playerInfo(ctx.arg(0));
        String uuid = (target != null) ? target.id : (ctx.arg(0).startsWith("#") ? null : ctx.arg(0));
        String ip = (target != null) ? target.lastIP : null;
        String name = (target != null) ? target.lastName : "Unknown";

        if (ctx.arg(0).startsWith("#")) {
            var data = database.getPlayerDataRepository().findById(Strings.parseInt(ctx.arg(0).substring(1)));
            if (data == null) {
                Log.err("Player not found");
                return;
            }
            uuid = data.uuid;
            name = data.nickname;
            var info = netServer.admins.getInfoOptional(uuid);
            if (info != null) ip = info.lastIP;
        }

        Instant period = time.parsePeriod(ctx.arg(1), TimeUnit.DAYS);
        if (period == null) {
            Log.err("Invalid period format.");
            return;
        }

        Instant expire = Instant.now().plusMillis(period.toEpochMilli());
        network.post(new SocketEvents.KickBannedPlayer(uuid, ip));

        BanData ban = BanData.builder()
                .name(name).uuid(uuid).ip(ip).adminName("console")
                .reason(ctx.args().length > 2 ? ctx.arg(2) : "Not Specified")
                .expireDate(expire).build();

        network.post(ban);
        database.getBanDataRepository().save(ban);
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
        database.getBanDataRepository().delete(uuid, ip);
        Log.info("Unbanned: UUID=@ / IP=@", uuid, ip);
    }

    @Command(name = "tempbans", params = "[search...]", description = "List current temp bans.")
    public void tempBans(ServerContext ctx) {
        // todo: paged
        Seq<BanData> bans = Seq.with(database.getBanDataRepository().findAll());
        if (ctx.args().length > 0) {
            String q = ctx.arg(0);
            bans.select(b -> deepEquals(b.name, q) || equalsNonEmpty(b.ip, q) || equalsNonEmpty(b.uuid, q));
        }
        bans.each(b -> Log.info("Ban: @ (@) until @. Reason: @", b.name, b.uuid,
                b.expireDate.atZone(ZoneId.systemDefault()).toLocalDateTime(), b.reason));
    }

    @Command(name = "mute", params = "<uuid/#id> <period> [reason...]", description = "Mute player.")
    public void mute(ServerContext ctx) {
        PlayerData data = find.playerData(ctx.arg(0));
        if (data == null) return;
        Instant period = time.parsePeriod(ctx.arg(1), TimeUnit.HOURS);
        if (period == null) return;

        MuteData m = MuteData.builder().uuid(data.uuid).name(data.nickname).adminName("console")
                .reason(ctx.args().length > 2 ? ctx.arg(2) : "Not Specified")
                .expireDate(Instant.now().plusMillis(period.toEpochMilli())).build();
        database.getMuteDataRepository().save(m);
        network.post(m);
        Log.info("Muted @ for @ minutes.", data.nickname, Duration.ofMillis(period.toEpochMilli()).toMinutes());
    }

    @Command(name = "unmute", params = "<uuid/#id>", description = "Unmute player.")
    public void unmute(ServerContext ctx) {
        PlayerData data = find.playerData(ctx.arg(0));
        if (data != null) database.getMuteDataRepository().delete(data.uuid);
        Log.info("Unmuted @", data != null ? data.nickname : "unknown");
    }
}
