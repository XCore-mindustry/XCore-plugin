package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;
import org.incendo.cloud.annotations.Default;

import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.DefaultUnit;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.service.BundleService;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.moderation.ModerationService;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
@Permission("admin")
public class ModerationController implements CloudClientController {

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

    @Command("ban <id> <period> [reason]")
    public void ban(XCoreSender sender,
                    @Argument("id") int id,
                    @Argument("period") @DefaultUnit(TimeUnit.DAYS) Duration period,
                    @Argument("reason") @Greedy @Default("") String reason) {

        Instant duration = Instant.ofEpochMilli(period.toMillis());

        var result = moderationService.banById(
                id,
                sender.player().name,
                reason.isBlank() ? null : reason,
                duration,
                true
        );

        if (result.isSuccess()) {
            sender.send("commands-ban-success", args("nickname", result.getData().get().name));
        } else {
            sender.send("error-player-not-found", args());
        }
    }

    @Command("unban <id>")
    public void unban(XCoreSender sender, @Argument("id") int id) {
        var result = moderationService.unbanById(id);

        if (result.isSuccess()) {
            var target = result.getData().get();
            sender.send("commands-unban-success", args(
                    "nickname", target.nickname,
                    "pid", target.pid
            ));
        } else {
            sender.send("error-player-not-found", args());
        }
    }

    @Command("mute <id> <period> [reason]")
    public void mute(XCoreSender sender,
                     @Argument("id") int id,
                     @Argument("period") @DefaultUnit(TimeUnit.HOURS) Duration period,
                     @Argument("reason") @Greedy @Default("") String reason) {

        Instant duration = Instant.ofEpochMilli(period.toMillis());

        var result = moderationService.muteById(
                id,
                sender.player().name,
                reason.isBlank() ? null : reason,
                duration
        );

        if (result.isSuccess()) {
            var mute = result.getData().get();
            sender.send("commands-mute-success", args("nickname", mute.name));

            Player p = find.playerByUuid(mute.uuid);
            if (p != null) {
                bundle.send(p, "you-are-muted-by", args(
                        "adminName", sender.player().coloredName(),
                        "reason", mute.reason,
                        "remainMinutes", period.toMinutes()
                ));
            }
        } else {
            sender.send("error-player-not-found", args());
        }
    }

    @Command("unmute <id>")
    public void unmute(XCoreSender sender, @Argument("id") int id) {
        var result = moderationService.unmuteById(id);

        if (result.isSuccess()) {
            sender.send("commands-unmute-success",
                    args("nickname", result.getData().get().nickname));
        } else {
            sender.send("error-player-not-found", args());
        }
    }
}
