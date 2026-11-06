package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;

import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.DefaultUnit;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.permission.TargetHierarchy;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.service.moderation.BanCommand;
import org.xcore.plugin.service.moderation.ModerationActor;
import org.xcore.plugin.service.moderation.ModerationResult;
import org.xcore.plugin.service.moderation.ModerationService;
import org.xcore.plugin.service.moderation.MuteCommand;
import org.xcore.plugin.service.moderation.UnbanCommand;
import org.xcore.plugin.service.moderation.UnmuteCommand;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.AuditHistoryMenu;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class ModerationController implements CloudClientController {

    private final ModerationService moderationService;
    private final FindService find;
    private final SessionService sessionService;
    private final AuditHistoryMenu auditHistoryMenu;
    private final Async async;
    private final TargetHierarchy hierarchy;

    @Inject
    public ModerationController(ModerationService moderationService, FindService find, SessionService sessionService,
                                AuditHistoryMenu auditHistoryMenu, Async async, TargetHierarchy hierarchy) {
        this.moderationService = moderationService;
        this.find = find;
        this.sessionService = sessionService;
        this.auditHistoryMenu = auditHistoryMenu;
        this.async = async;
        this.hierarchy = hierarchy;
    }

    public ModerationController(ModerationService moderationService, FindService find, SessionService sessionService) {
        this(moderationService, find, sessionService, null, null, TargetHierarchy.none());
    }

    @Permission(PermissionNodes.MODERATION_BAN)
    @Command("ban <target> <period> [reason]")
    public void ban(XCoreSender sender,
                    @Argument("target") String target,
                    @Argument("period") @DefaultUnit(TimeUnit.DAYS) Duration period,
                    @Argument("reason") @Greedy String reason) {

        Session session = resolveActiveSession(sender);
        if (session == null) return;
        Localization local = session.locale();

        var actor = ModerationActor.of(sender.actor());
        guarded(session, target, PermissionNodes.MODERATION_BAN, () -> {
            var result = moderationService.banByTarget(target, actor.name(), actor.discordId(), reason, period, true);

            if (result.isSuccess()) {
                local.send("commands-ban-success", args("nickname", result.getData().get().name));
            } else {
                sendModerationFailure(local, result);
            }
        });
    }

    @Permission(PermissionNodes.MODERATION_UNBAN)
    @Command("unban <target>")
    public void unban(XCoreSender sender, @Argument("target") String target) {
        Session session = resolveActiveSession(sender);
        if (session == null) return;
        Localization local = session.locale();

        var actor = ModerationActor.of(sender.actor());
        guarded(session, target, PermissionNodes.MODERATION_UNBAN, () -> {
            var result = moderationService.unbanByTarget(target, actor.name(), actor.discordId());

            if (result.isSuccess()) {
                var pData = result.getData().get();
                local.send("commands-unban-success", args(
                        "nickname", pData.nickname,
                        "pid", pData.pid
                ));
            } else {
                sendModerationFailure(local, result);
            }
        });
    }

    /**
     * Lifts only the IP of one ban: the banned account stays banned by UUID, and other players
     * on that address (a shared or mobile IP) can join again.
     */
    @Permission(PermissionNodes.MODERATION_UNBAN)
    @Command("unbanip <ban>")
    public void unbanIp(XCoreSender sender, @Argument("ban") String ban) {
        Session session = resolveActiveSession(sender);
        if (session == null) return;
        Localization local = session.locale();

        Integer banId = ModerationService.parseBanId(ban);
        if (banId == null) {
            local.send("commands-unbanip-invalid", args());
            return;
        }

        var actor = ModerationActor.of(sender.actor());
        hierarchy.whenAllowed(session, () -> {
            var found = moderationService.findBan(banId);
            return found == null || found.uuid == null || found.uuid.isBlank() ? null : found.uuid;
        }, PermissionNodes.MODERATION_UNBAN, () -> {
            var result = moderationService.releaseBanIp(banId, actor.name(), actor.discordId());
            if (result.isSuccess()) {
                var release = result.getData().get();
                var released = release.ban();
                local.send("commands-unbanip-success", args(
                        "banId", banId,
                        "nickname", released.name,
                        "pid", PlayerPids.isAssigned(released.pid) ? released.pid : "-"
                ));
                if (!release.otherBanIds().isEmpty()) {
                    local.send("commands-unbanip-still-banned",
                            args("bans", ModerationService.banList(release.otherBanIds())));
                }
                return;
            }
            String key = switch (result.getMessage().orElse("")) {
                case ModerationService.BAN_NOT_FOUND_MESSAGE -> "commands-unbanip-not-found";
                case ModerationService.BAN_HAS_NO_IP_MESSAGE -> "commands-unbanip-no-ip";
                case ModerationService.BAN_HAS_NO_UUID_MESSAGE -> "commands-unbanip-no-uuid";
                default -> "error-processing-request";
            };
            local.send(key, args("banId", banId));
        }, () -> local.send(TargetHierarchy.DENIED_KEY, args()));
    }

    @Permission(PermissionNodes.MODERATION_MUTE)
    @Command("mute <target> <period> [reason]")
    public void mute(XCoreSender sender,
                     @Argument("target") String target,
                     @Argument("period") @DefaultUnit(TimeUnit.HOURS) Duration period,
                     @Argument("reason") @Greedy String reason) {
        Session session = resolveActiveSession(sender);
        if (session == null) return;
        Localization local = session.locale();

        var actor = ModerationActor.of(sender.actor());
        guarded(session, target, PermissionNodes.MODERATION_MUTE, () -> {
            var result = moderationService.muteByTarget(target, actor.name(), actor.discordId(), reason, period);

            if (result.isSuccess()) {
                var mute = result.getData().get();
                local.send("commands-mute-success", args("nickname", mute.name));

                Player p = find.playerByUuid(mute.uuid);
                if (p != null) {
                    Session s = sessionService.get(p.uuid());
                    if (s != null) {
                        s.locale().send("you-are-muted-by", SecurityService.muteMessageArgs(sender.player().coloredName(), mute.reason, period));
                    }
                }
            } else {
                sendModerationFailure(local, result);
            }
        });
    }

    @Permission(PermissionNodes.MODERATION_UNMUTE)
    @Command("unmute <target>")
    public void unmute(XCoreSender sender, @Argument("target") String target) {
        Session session = resolveActiveSession(sender);
        if (session == null) return;
        Localization local = session.locale();

        var actor = ModerationActor.of(sender.actor());
        guarded(session, target, PermissionNodes.MODERATION_UNMUTE, () -> {
            var result = moderationService.unmuteByTarget(target, actor.name(), actor.discordId());

            if (result.isSuccess()) {
                local.send("commands-unmute-success",
                        args("nickname", result.getData().get().nickname));
            } else {
                sendModerationFailure(local, result);
            }
        });
    }

    @Permission(PermissionNodes.MODERATION_AUDIT_OTHERS)
    @Command("audit [target]")
    public void audit(XCoreSender sender, @Argument("target") @Default("") String target) {
        Session session = resolveActiveSession(sender);
        if (session == null || auditHistoryMenu == null) return;
        Localization local = session.locale();

        if (target.isBlank()) {
            auditHistoryMenu.history(session.data.uuid, session.data);
            return;
        }

        PlayerData targetData = moderationService.resolvePlayerData(target);
        if (targetData == null) {
            local.send("error-player-not-found", args());
            return;
        }

        auditHistoryMenu.history(session.data.uuid, targetData);
    }
    /**
     * Runs {@code action} unless the target's roles do not weigh less than the actor's, which
     * the actor is told. Finding the target and their roles takes the store, so with roles on
     * the action runs a little later, back on the game thread.
     */
    private void guarded(Session actor, String target, String node, Runnable action) {
        hierarchy.whenAllowed(actor, () -> {
            PlayerData targetData = moderationService.resolvePlayerData(target);
            return targetData == null ? null : targetData.uuid;
        }, node, action, () -> actor.locale().send(TargetHierarchy.DENIED_KEY, args()));
    }

    private Session resolveActiveSession(XCoreSender sender) {
        Session session = resolveSession(sender, sessionService);
        return (session != null && session.data != null) ? session : null;
    }

    private static void sendModerationFailure(Localization local, ModerationResult<?> result) {
        var message = result.getMessage().orElse(null);
        if (ModerationService.PLAYER_NOT_FOUND_MESSAGE.equals(message)) {
            local.send("error-player-not-found", args());
            return;
        }
        local.send("error-processing-request", args());
    }
}
