package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.command.core.annotation.AdminOnly;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.moderation.ModerationService;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

import org.xcore.plugin.command.core.ClientController;

@Singleton
@AdminOnly
public class ModerationController implements ClientController {

    private final ModerationService moderationService;
    private final BundleService bundle;
    private final FindService find;

    @Inject
    public ModerationController(ModerationService moderationService,
                                BundleService bundle,
                                FindService find) {
        this.moderationService = moderationService;
        this.bundle = bundle;
        this.find = find;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Command(name = "ban", params = "<id> <period> [reason...]")
    public void ban(ClientContext ctx) {
        int id = ctx.argInt(0, -1);

        var period = moderationService.parsePeriod(ctx.arg(1), TimeUnit.DAYS);
        if (period == null) {
            ctx.send("error-wrong-period-format", args());
            return;
        }

        String reason = ctx.args().length > 2 ? ctx.args()[2] : null;
        var result = moderationService.banById(id, ctx.player().name, reason, period, true);

        if (result.isSuccess()) {
            ctx.send("commands-ban-success", args("nickname", result.getData().get().name));
        } else {
            ctx.send("error-player-not-found", args());
        }
    }

    @Command(name = "unban", params = "<id>")
    public void unban(ClientContext ctx) {
        int id = ctx.argInt(0, -1);

        var result = moderationService.unbanById(id);

        if (result.isSuccess()) {
            var target = result.getData().get();
            ctx.send("commands-unban-success", args(
                    "nickname", target.nickname,
                    "pid", target.pid
            ));
        } else {
            ctx.send("error-player-not-found", args());
        }
    }

    @Command(name = "mute", params = "<id> <period> [reason...]")
    public void mute(ClientContext ctx) {
        int id = ctx.argInt(0, -1);

        var period = moderationService.parsePeriod(ctx.arg(1), TimeUnit.HOURS);
        if (period == null) {
            ctx.send("error-wrong-period-format", args());
            return;
        }

        String reason = (ctx.args().length > 2) ? ctx.args()[2] : null;
        var result = moderationService.muteById(id, ctx.player().name, reason, period);

        if (result.isSuccess()) {
            var mute = result.getData().get();
            ctx.send("commands-mute-success", args("nickname", mute.name));

            Player p = find.playerByUuid(mute.uuid);
            if (p != null) {
                bundle.send(p, "you-are-muted-by", args(
                        "adminName", ctx.player().coloredName(),
                        "reason", mute.reason,
                        "remainMinutes", Duration.ofMillis(period.toEpochMilli()).toMinutes()
                ));
            }
        } else {
            ctx.send("error-player-not-found", args());
        }
    }

    @Command(name = "unmute", params = "<id>")
    public void unmute(ClientContext ctx) {
        int id = ctx.argInt(0, -1);

        var result = moderationService.unmuteById(id);

        if (result.isSuccess()) {
            ctx.send("commands-unmute-success", args("nickname", result.getData().get().nickname));
        } else {
            ctx.send("error-player-not-found", args());
        }
    }
}
