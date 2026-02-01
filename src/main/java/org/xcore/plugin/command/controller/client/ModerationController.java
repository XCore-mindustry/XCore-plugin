package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.command.core.annotation.AdminOnly;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.PlayerSessionService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
@AdminOnly
public class ModerationController {

    private final PlayerDataRepository playerDataRepository;
    private final BanDataRepository banDataRepository;
    private final MuteDataRepository muteDataRepository;
    private final PlayerSessionService playerSessionService;
    private final NetworkService network;
    private final BundleService bundle;
    private final FindService find;
    private final TimeService time;

    @Inject
    public ModerationController(PlayerDataRepository playerDataRepository,
                                BanDataRepository banDataRepository,
                                MuteDataRepository muteDataRepository,
                                PlayerSessionService playerSessionService,
                                NetworkService network,
                                BundleService bundle,
                                FindService find,
                                TimeService timeService) {
        this.playerDataRepository = playerDataRepository;
        this.banDataRepository = banDataRepository;
        this.muteDataRepository = muteDataRepository;
        this.playerSessionService = playerSessionService;
        this.network = network;
        this.bundle = bundle;
        this.find = find;
        this.time = timeService;
    }

    @Command(name = "ban", params = "<id> <period> [reason...]")
    public void ban(ClientContext ctx) {
        int id = ctx.argInt(0, -1);
        var target = playerDataRepository.findById(id);

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
        banDataRepository.save(ban);

        ctx.send("commands-ban-success", args("nickname", target.nickname));
    }

    @Command(name = "unban", params = "<id>")
    public void unban(ClientContext ctx) {
        int id = ctx.argInt(0, -1);
        var target = playerDataRepository.findById(id);

        if (target == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        banDataRepository.delete(target.uuid, null);

        ctx.send("commands-unban-success", args(
                "nickname", target.nickname,
                "pid", target.pid
        ));
    }

    @Command(name = "mute", params = "<id> <period> [reason...]")
    public void mute(ClientContext ctx) {
        int id = ctx.argInt(0, -1);
        var target = playerSessionService.getOrLoadFromDb(id);

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

        muteDataRepository.save(MuteData.builder()
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
        var target = playerSessionService.getOrLoadFromDb(id);

        if (target == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        muteDataRepository.delete(target.uuid);

        ctx.send("commands-unmute-success", args("nickname", target.nickname));
    }
}
