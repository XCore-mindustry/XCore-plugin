package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Permission;

import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.DefaultUnit;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.moderation.ModerationService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
@Permission("admin")
public class ModerationController implements CloudClientController {

    private final ModerationService moderationService;
    private final FindService find;
    private final SessionService sessionService;

    @Inject
    public ModerationController(ModerationService moderationService, FindService find, SessionService sessionService) {
        this.moderationService = moderationService;
        this.find = find;
        this.sessionService = sessionService;
    }

    @Command("ban <id> <period> [reason]")
    public void ban(XCoreSender sender,
                    @Argument("id") int id,
                    @Argument("period") @DefaultUnit(TimeUnit.DAYS) Duration period,
                    @Argument("reason") @Greedy String reason) {

        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        Localization local = session.locale();

        var result = moderationService.banById(
                id,
                session.player.name,
                reason == null || reason.isBlank() ? null : reason,
                period,
                true
        );

        if (result.isSuccess()) {
            local.send("commands-ban-success", args("nickname", result.getData().get().name));
        } else {
            local.send("error-player-not-found", args());
        }
    }

    @Command("unban <id>")
    public void unban(XCoreSender sender, @Argument("id") int id) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        Localization local = session.locale();

        var result = moderationService.unbanById(id);

        if (result.isSuccess()) {
            var target = result.getData().get();
            local.send("commands-unban-success", args(
                    "nickname", target.nickname,
                    "pid", target.pid
            ));
        } else {
            local.send("error-player-not-found", args());
        }
    }

    @Command("mute <id> <period> [reason]")
    public void mute(XCoreSender sender,
                     @Argument("id") int id,
                     @Argument("period") @DefaultUnit(TimeUnit.HOURS) Duration period,
                     @Argument("reason") @Greedy String reason) {

        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        Localization local = session.locale();

        var result = moderationService.muteById(
                id,
                session.player.name,
                reason == null || reason.isBlank() ? null : reason,
                period
        );

        if (result.isSuccess()) {
            var mute = result.getData().get();
            local.send("commands-mute-success", args("nickname", mute.name));

            Player p = find.playerByUuid(mute.uuid);
            if (p != null) {
                Session s = sessionService.get(p.uuid());
                s.locale().send("you-are-muted-by", args(
                        "adminName", sender.player().coloredName(),
                        "reason", mute.reason,
                        "remainMinutes", period.toMinutes()
                ));
            }
        } else {
            local.send("error-player-not-found", args());
        }
    }

    @Command("unmute <id>")
    public void unmute(XCoreSender sender, @Argument("id") int id) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        Localization local = session.locale();

        var result = moderationService.unmuteById(id);

        if (result.isSuccess()) {
            local.send("commands-unmute-success",
                    args("nickname", result.getData().get().nickname));
        } else {
            local.send("error-player-not-found", args());
        }
    }
}
