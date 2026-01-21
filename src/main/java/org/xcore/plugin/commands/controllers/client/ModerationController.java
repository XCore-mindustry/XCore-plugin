package org.xcore.plugin.commands.controllers.client;

import mindustry.gen.Player;
import org.xcore.plugin.infra.commands.annotation.*;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.NetSock;
import org.xcore.plugin.utils.Utils;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.MuteData;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.*;
import static org.xcore.plugin.PluginVars.*;

@SuppressWarnings("unused")
@AdminOnly
public class ModerationController {

    @Command(name = "ban", params = "<id> <period> [reason...]")
    public void ban(ClientContext ctx) {
        int id = ctx.argInt(0, -1);
        var target = database.getPlayerDatas().getById(id);

        if (target == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        Instant period = Utils.parsePeriod(ctx.arg(1), TimeUnit.DAYS);
        if (period == null) {
            ctx.send("error-wrong-period-format", args());
            return;
        }

        Instant unbanDate = Instant.now().plusMillis(period.toEpochMilli());
        var info = mindustry.Vars.netServer.admins.getInfoOptional(target.uuid);
        String ip = (info != null) ? info.lastIP : null;

        NetSock.post(new SocketEvents.KickBannedPlayer(target.uuid, ip));

        BanData ban = BanData.builder()
                .name(target.nickname)
                .uuid(target.uuid)
                .ip(ip)
                .adminName(ctx.player().name)
                .reason(ctx.args().length > 2 ? ctx.args()[2] : "Not Specified")
                .expireDate(unbanDate)
                .build();

        NetSock.post(ban);
        ban.save();

        ctx.send("commands-ban-success", args(
                "nickname", target.nickname
        ));
    }

    @Command(name = "unban", params = "<id>")
    public void unban(ClientContext ctx) {
        int id = ctx.argInt(0, -1);
        var target = database.getPlayerDatas().getById(id);

        if (target == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        database.getBanDatas().delete(target.uuid, null);

        ctx.send("commands-unban-success", args(
                "nickname", target.nickname,
                "pid", target.pid
        ));
    }

    @Command(name = "mute", params = "<id> <period> [reason...]")
    public void mute(ClientContext ctx) {
        int id = ctx.argInt(0, -1);
        var target = database.getCachedOrDb(id);

        if (target == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        Instant period = Utils.parsePeriod(ctx.arg(1), TimeUnit.HOURS);
        if (period == null) {
            ctx.send("error-wrong-period-format", args());
            return;
        }

        String reason = (ctx.args().length > 2) ? ctx.args()[2] : "Not Specified";
        Instant expireDate = Instant.now().plusMillis(period.toEpochMilli());

        database.muteDatas.save(MuteData.builder()
                .uuid(target.uuid)
                .name(target.nickname)
                .adminName(ctx.player().name)
                .reason(reason)
                .expireDate(expireDate)
                .build()
        );

        ctx.send("commands-mute-success", args(
                "nickname", target.nickname
        ));

        Player p = Find.playerByUuid(target.uuid);
        if (p != null) {
            bundle.send(p, "you-are-muted-by", args(
                    "adminName", ctx.player().coloredName(),
                    "reason", reason,
                    "remainMinutes", Duration.ofMillis(period.toEpochMilli()).toMinutes()
            ));
        }
    }

    @Command(name = "unmute", params = "<id>")
    public void unmute(ClientContext ctx) {
        int id = ctx.argInt(0, -1);
        var target = database.getCachedOrDb(id);

        if (target == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        database.muteDatas.delete(target.uuid);

        ctx.send("commands-unmute-success", args(
                "nickname", target.nickname
        ));
    }
}