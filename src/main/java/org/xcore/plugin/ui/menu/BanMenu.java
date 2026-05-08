package org.xcore.plugin.ui.menu;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.service.moderation.ModerationService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.flow.MenuPrompt;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class BanMenu extends Menu {

    private static final String PROMPT_DURATION = "ban-duration";
    private static final String PROMPT_REASON = "ban-reason";

    private final ModerationService moderationService;
    private final TimeService timeService;

    @Inject
    public BanMenu(Config config,
                   GlobalConfig globalConfig,
                   SessionService sessionService,
                   ModerationService moderationService,
                   TimeService timeService) {
        super(config, globalConfig, sessionService);
        this.moderationService = moderationService;
        this.timeService = timeService;
    }

    public void open(Player admin, Player target) {
        var session = sessionService.get(admin);
        if (session == null || session.data == null || target == null) return;

        var targetData = sessionService.getOrLoadFromDb(target.uuid());
        if (targetData == null) {
            session.locale().send("error-player-not-found", args());
            return;
        }

        session.setDraft(new BanDraft(target.uuid(), targetData.pid, target.coloredName(), target.plainName()));
        askDuration(session);
    }

    private void askDuration(Session session) {
        var draft = session.getDraft(BanDraft.class);
        if (draft == null) {
            session.locale().send("error-internal", args());
            return;
        }

        var prompt = new MenuPrompt(
                PROMPT_DURATION,
                session.locale().t("ban-menu-duration-title"),
                session.locale().t("ban-menu-duration-message", args("nickname", draft.targetColoredName)),
                64,
                draft.durationInput,
                false
        );

        session.menuService.openPrompt(session, prompt, text -> {
            String durationInput = text == null ? "" : text.trim();
            var parsed = timeService.parsePeriod(durationInput, TimeUnit.DAYS);
            if (parsed == null || parsed.toEpochMilli() <= 0) {
                session.locale().send("error-wrong-period-format", args());
                askDuration(session);
                return;
            }

            draft.durationInput = durationInput;
            draft.duration = Duration.ofMillis(parsed.toEpochMilli());
            askReason(session);
        }, () -> cancel(session));
    }

    private void askReason(Session session) {
        var draft = session.getDraft(BanDraft.class);
        if (draft == null) {
            session.locale().send("error-internal", args());
            return;
        }

        var prompt = new MenuPrompt(
                PROMPT_REASON,
                session.locale().t("ban-menu-reason-title"),
                session.locale().t("ban-menu-reason-message", args("nickname", draft.targetColoredName)),
                256,
                draft.reason == null ? "" : draft.reason,
                false
        );

        session.menuService.openPrompt(session, prompt, text -> {
            draft.reason = (text == null || text.trim().isEmpty()) ? null : text.trim();
            confirm(session);
        }, () -> askDuration(session));
    }

    private void confirm(Session session) {
        var draft = session.getDraft(BanDraft.class);
        if (draft == null || draft.duration == null) {
            session.locale().send("error-internal", args());
            return;
        }

        session.clear();
        session.builder()
                .title("ban-menu-confirm-title")
                .content("ban-menu-confirm-content", args(
                        "nickname", draft.targetColoredName,
                        "duration", draft.durationInput,
                        "reason", draft.reason == null ? session.locale().t("none") : draft.reason
                ))
                .addLocalRow("ban-menu-confirm-action", () -> applyBan(session), "cancel", () -> cancel(session))
                .showFollowUp();
    }

    private void applyBan(Session session) {
        var draft = session.getDraft(BanDraft.class);
        if (draft == null || draft.duration == null) {
            session.locale().send("error-internal", args());
            session.menuService.close(session);
            return;
        }

        var result = moderationService.banById(draft.targetPid, session.player.name, session.data.discordId, draft.reason, draft.duration, true);
        session.clearDraft(BanDraft.class);

        if (!result.isSuccess() || result.getData().isEmpty()) {
            session.locale().send("error-player-not-found", args());
            session.menuService.close(session);
            return;
        }

        BanData ban = result.getData().get();
        sessionService.broadcast("tempban-player-banned", args(
                "adminName", session.player.coloredName(),
                "playerName", draft.targetColoredName
        ));
        arc.util.Log.info("@ banned @ (@) for @", session.player.plainName(), draft.targetPlainName, draft.targetUuid, draft.durationInput);
        session.locale().send("commands-ban-success", args("nickname", ban.name));
        session.menuService.close(session);
    }

    private void cancel(Session session) {
        var draft = session.getDraft(BanDraft.class);
        String nickname = draft == null ? session.locale().t("none") : draft.targetColoredName;
        session.clearDraft(BanDraft.class);
        session.locale().send("ban-cancelled", args("nickname", nickname));
        session.menuService.close(session);
    }

    private static final class BanDraft {
        private final String targetUuid;
        private final int targetPid;
        private final String targetColoredName;
        private final String targetPlainName;
        private String durationInput = "1d";
        private Duration duration;
        private String reason;

        private BanDraft(String targetUuid, int targetPid, String targetColoredName, String targetPlainName) {
            this.targetUuid = targetUuid;
            this.targetPid = targetPid;
            this.targetColoredName = targetColoredName;
            this.targetPlainName = targetPlainName;
        }
    }
}
