package org.xcore.plugin.ui.menu;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.service.moderation.ModerationService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class BanMenu extends Menu {

    private static final String ROUTE_FLOW = "ban.flow";
    private static final String PROMPT_DURATION = "ban-duration";
    private static final String PROMPT_REASON = "ban-reason";
    private static final String ACTION_APPLY = "apply";
    private static final String ACTION_CANCEL = "cancel";

    private final ModerationService moderationService;
    private final TimeService timeService;
    private final MenuService menuService;

    @Inject
    public BanMenu(Config config,
                   GlobalConfig globalConfig,
                   SessionService sessionService,
                   ModerationService moderationService,
                   TimeService timeService,
                   MenuService menuService) {
        super(config, globalConfig, sessionService);
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

    private final class BanFlow implements RoutedMenuFlow<BanFlowState> {
        @Override
        public String routeId() {
            return ROUTE_FLOW;
        }

        @Override
        public BanFlowState createState(Session session, MenuRoute route, BanFlowState currentState) {
            return currentState == null ? new BanFlowState() : currentState;
        }

        @Override
        public Class<BanFlowState> stateType() {
            return BanFlowState.class;
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
                    return MenuScreen.followUp(
                            local.t("ban-menu-confirm-title"),
                            local.t("ban-menu-confirm-content", args(
                                    "nickname", state.targetColoredName,
                                    "duration", state.durationInput,
                                    "reason", state.reason == null ? local.t("none") : state.reason
                            )),
                            List.of(List.of(
                                    MenuButton.of(local.t("ban-menu-confirm-action"), ACTION_APPLY),
                                    MenuButton.of(local.t("cancel"), ACTION_CANCEL)
                            ))
                    );
                }
                default -> {
                    return placeholderScreen();
                }
            }
        }

        @Override
        public void onAction(MenuRenderContext<BanFlowState> context, String actionId) {
            switch (actionId) {
                case ACTION_APPLY -> applyBan(context);
                case ACTION_CANCEL -> cancel(context);
            }
        }

        @Override
        public void onPromptSubmit(MenuRenderContext<BanFlowState> context, String promptId, String text) {
            var state = context.state();
            var session = context.session();

            switch (promptId) {
                case PROMPT_DURATION -> {
                    String durationInput = text == null ? "" : text.trim();
                    var parsed = timeService.parsePeriod(durationInput, TimeUnit.DAYS);
                    if (parsed == null || parsed.toEpochMilli() <= 0) {
                        session.locale().send("error-wrong-period-format", args());
                        context.render();
                        return;
                    }
                    state.durationInput = durationInput;
                    state.duration = Duration.ofMillis(parsed.toEpochMilli());
                    state.step = BanFlowState.Step.REASON;
                    context.render();
                }
                case PROMPT_REASON -> {
                    state.reason = (text == null || text.trim().isEmpty()) ? null : text.trim();
                    state.step = BanFlowState.Step.CONFIRM;
                    context.render();
                }
            }
        }

        @Override
        public void onPromptCancel(MenuRenderContext<BanFlowState> context, String promptId) {
            var state = context.state();

            switch (promptId) {
                case PROMPT_DURATION -> cancel(context);
                case PROMPT_REASON -> {
                    state.step = BanFlowState.Step.DURATION;
                    context.render();
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
        arc.util.Log.info("@ banned @ (@) for @", session.player.plainName(), state.targetPlainName, state.targetUuid, state.durationInput);
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
