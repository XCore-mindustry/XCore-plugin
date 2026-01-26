package org.xcore.plugin.commands.controllers.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.infra.commands.annotation.AdminOnly;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.modules.bundles.BundleService;
import org.xcore.plugin.modules.common.TimeService;
import org.xcore.plugin.modules.database.DatabaseService;
import org.xcore.plugin.modules.network.NetworkService;
import org.xcore.plugin.utils.FindService;
import org.xcore.plugin.utils.models.BanData;
import org.xcore.plugin.utils.models.MuteData;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
@AdminOnly
public class ModerationController {

    private final DatabaseService database;
    private final NetworkService network;
    private final BundleService bundle;
    private final FindService find;
    private final TimeService time;

    @Inject
    public ModerationController(DatabaseService database, NetworkService network, BundleService bundle,
                                FindService find, TimeService timeService) {
        this.database = database;
        this.network = network;
        this.bundle = bundle;
        this.find = find;
        this.time = timeService;
    }

    @Command(name = "ban", params = "<id> <period> [reason...]")
    public void ban(ClientContext ctx) {
        int id = ctx.argInt(0, -1);
        var target = database.getPlayerDataRepository().findById(id);

        if (target == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        Instant period = time.parsePeriod(ctx.arg(1), TimeUnit.DAYS);
        if (period == null) {
            ctx.send("error-wrong-period-format", args());
            return;
        }

        Instant unbanDate = Instant.now().plusMillis(period.toEpochMilli());
        var info = mindustry.Vars.netServer.admins.getInfoOptional(target.uuid);
        String ip = (info != null) ? info.lastIP : null;

        network.post(new SocketEvents.KickBannedPlayer(target.uuid, ip));

        BanData ban = BanData.builder()
                .name(target.nickname)
                .uuid(target.uuid)
                .ip(ip)
                .adminName(ctx.player().name)
                .reason(ctx.args().length > 2 ? ctx.args()[2] : "Not Specified")
                .expireDate(unbanDate)
                .build();

        network.post(ban);
        database.getBanDataRepository().save(ban);

        ctx.send("commands-ban-success", args("nickname", target.nickname));
    }

    @Command(name = "unban", params = "<id>")
    public void unban(ClientContext ctx) {
        int id = ctx.argInt(0, -1);
        var target = database.getPlayerDataRepository().findById(id);

        if (target == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        database.getBanDataRepository().delete(target.uuid, null);

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

        Instant period = time.parsePeriod(ctx.arg(1), TimeUnit.HOURS);
        if (period == null) {
            ctx.send("error-wrong-period-format", args());
            return;
        }

        String reason = (ctx.args().length > 2) ? ctx.args()[2] : "Not Specified";
        Instant expireDate = Instant.now().plusMillis(period.toEpochMilli());

        database.getMuteDataRepository().save(MuteData.builder()
                .uuid(target.uuid)
                .name(target.nickname)
                .adminName(ctx.player().name)
                .reason(reason)
                .expireDate(expireDate)
                .build()
        );

        ctx.send("commands-mute-success", args("nickname", target.nickname));

        Player p = find.playerByUuid(target.uuid);
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

        database.getMuteDataRepository().delete(target.uuid);

        ctx.send("commands-unmute-success", args("nickname", target.nickname));
    }
}
