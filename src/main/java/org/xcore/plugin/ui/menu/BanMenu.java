package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.service.moderation.ModerationService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class BanMenu extends Menu {

    private static final String ROUTE_FLOW = "ban.flow";
    private static final String PROMPT_DURATION = "ban-duration";
    private static final String PROMPT_REASON = "ban-reason";

    private final ModerationService moderationService;
    private final TimeService timeService;
    private final MenuService menuService;

    @Inject
    public BanMenu(TomlSecretsConfig secretsConfig,
                   SessionService sessionService,
                   ModerationService moderationService,
                   TimeService timeService,
                   MenuService menuService) {
        super(secretsConfig, sessionService);
        this.moderationService = moderationService;
        this.timeService = timeService;
        this.menuService = menuService;
    }

    @PostConstruct
    public void init() {
        menuService.registerRoute(new BanFlow());
    }

    public void open(Player admin, Player target) {
        var session = sessionService.get(admin);
        if (session == null || session.data == null || target == null) return;

        var targetData = sessionService.getOrLoadFromDb(target.uuid());
        if (targetData == null) {
            session.locale().send("error-player-not-found", args());
            return;
        }

        var state = new BanFlowState();
        state.targetUuid = target.uuid();
        state.targetPid = targetData.pid;
        state.targetColoredName = target.coloredName();
        state.targetPlainName = target.plainName();
        state.step = BanFlowState.Step.DURATION;

        session.clear();
        session.setDraft(BanFlowState.class, state);
        session.menuService.renderRoute(session, MenuRoute.of(ROUTE_FLOW));
    }

    private final class BanFlow extends BaseMenuFlow<BanFlowState> {
        BanFlow() {
            super(ROUTE_FLOW, BanFlowState.class);
            action("apply", ctx -> applyBan(ctx));
            action("cancel", ctx -> cancel(ctx));

            onPrompt(PROMPT_DURATION, ctx -> {
                var state = ctx.renderContext().state();
                var session = ctx.renderContext().session();
                String durationInput = ctx.text() == null ? "" : ctx.text().trim();
                var parsed = timeService.parsePeriod(durationInput, TimeUnit.DAYS);
                if (parsed == null || parsed.toEpochMilli() <= 0) {
                    session.locale().send("error-wrong-period-format", args());
                    ctx.renderContext().render();
                    return;
                }
                state.durationInput = durationInput;
                state.duration = Duration.ofMillis(parsed.toEpochMilli());
                state.step = BanFlowState.Step.REASON;
                ctx.renderContext().render();
            }, ctx -> cancel(ctx));

            onPrompt(PROMPT_REASON, ctx -> {
                var state = ctx.renderContext().state();
                state.reason = (ctx.text() == null || ctx.text().trim().isEmpty()) ? null : ctx.text().trim();
                state.step = BanFlowState.Step.CONFIRM;
                ctx.renderContext().render();
            }, ctx -> {
                var state = ctx.state();
                state.step = BanFlowState.Step.DURATION;
                ctx.render();
            });
        }

        @Override
        public BanFlowState createState(Session session, MenuRoute route, BanFlowState currentState) {
            return currentState == null ? new BanFlowState() : currentState;
        }

        @Override
        public MenuScreen render(MenuRenderContext<BanFlowState> context) {
            var state = context.state();
            var local = context.locale();
            var session = context.session();

            if (state.targetUuid == null) {
                context.close();
                return placeholderScreen();
            }

            switch (state.step) {
                case DURATION -> {
                    context.openPrompt(new MenuPrompt(
                            PROMPT_DURATION,
                            local.t("ban-menu-duration-title"),
                            local.t("ban-menu-duration-message", args("nickname", state.targetColoredName)),
                            64,
                            state.durationInput,
                            false
                    ));
                    return placeholderScreen();
                }
                case REASON -> {
                    context.openPrompt(new MenuPrompt(
                            PROMPT_REASON,
                            local.t("ban-menu-reason-title"),
                            local.t("ban-menu-reason-message", args("nickname", state.targetColoredName)),
                            256,
                            state.reason == null ? "" : state.reason,
                            false
                    ));
                    return placeholderScreen();
                }
                case CONFIRM -> {
                    var grid = new MenuGrid();
                    grid.row(
                            MenuButton.of(local.t("ban-menu-confirm-action"), "apply"),
                            MenuButton.of(local.t("cancel"), "cancel")
                    );
                    return MenuScreen.followUp(
                            local.t("ban-menu-confirm-title"),
                            local.t("ban-menu-confirm-content", args(
                                    "nickname", state.targetColoredName,
                                    "duration", state.durationInput,
                                    "reason", state.reason == null ? local.t("none") : state.reason
                            )),
                            grid.build()
                    );
                }
                default -> {
                    return placeholderScreen();
                }
            }
        }

        private MenuScreen placeholderScreen() {
            return MenuScreen.normal("", "", List.of());
        }
    }

    private void applyBan(MenuRenderContext<BanFlowState> context) {
        var session = context.session();
        var state = context.state();
        if (state.duration == null) {
            session.locale().send("error-internal", args());
            context.close();
            return;
        }

        var result = moderationService.banById(state.targetPid, session.player.name, session.data.discordId, state.reason, state.duration, true);
        session.clearDraft(BanFlowState.class);

        if (!result.isSuccess() || result.getData().isEmpty()) {
            session.locale().send("error-player-not-found", args());
            context.close();
            return;
        }

        BanData ban = result.getData().get();
        sessionService.broadcast("tempban-player-banned", args(
                "adminName", session.player.coloredName(),
                "playerName", state.targetColoredName
        ));
        session.locale().send("commands-ban-success", args("nickname", ban.name));
        context.close();
    }

    private void cancel(MenuRenderContext<BanFlowState> context) {
        var session = context.session();
        var state = context.state();
        String nickname = state.targetColoredName == null ? session.locale().t("none") : state.targetColoredName;
        session.clearDraft(BanFlowState.class);
        session.locale().send("ban-cancelled", args("nickname", nickname));
        context.close();
    }

    public static final class BanFlowState {
        public String targetUuid;
        public int targetPid;
        public String targetColoredName;
        public String targetPlainName;
        public String durationInput = "1d";
        public Duration duration;
        public String reason;
        public Step step = Step.DURATION;

        public enum Step {
            DURATION,
            REASON,
            CONFIRM
        }
    }
}
