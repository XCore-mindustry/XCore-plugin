package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import arc.util.Strings;
import com.ospx.flubundle.Bundle;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.BaseMenuFlow;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuGrid;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import static com.ospx.flubundle.Bundle.args;

final class PlayerSettingsFlows {

    static final String ROUTE_CHAT_SETTINGS = "player.chat-settings";
    static final String ROUTE_BADGES = "player.badges";
    static final String ROUTE_ALL_BADGES = "player.all-badges";
    static final String ROUTE_SETTINGS = "player.settings";
    static final String ROUTE_LANGUAGE_SELECTION = "player.language-selection";
    static final String ROUTE_BADGE_SYMBOL_COLOR = "player.badge-symbol-color";

    private static final String ACTION_LANGUAGE_PREFIX = "lang:";
    private static final String ACTION_BADGE_PREFIX = "badge:";

    private PlayerSettingsFlows() {
    }

    static PlayerData resolveTarget(PlayerProfileSettingsService profileSettings, Session session, String targetUuid) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return null;
        }
        if (session != null && session.data != null && targetUuid.equals(session.data.uuid)) {
            return session.data;
        }
        if (profileSettings != null) {
            return profileSettings.findByUuid(targetUuid);
        }
        return session != null && session.playerDataRepository != null
                ? session.playerDataRepository.findByUuid(targetUuid)
                : null;
    }

    abstract static class BasePlayerSettingsFlow<T> extends BaseMenuFlow<T> {
        protected final PlayerProfileSettingsService profileSettings;

        protected BasePlayerSettingsFlow(String routeId, Class<T> stateType, PlayerProfileSettingsService profileSettings) {
            super(routeId, stateType);
            this.profileSettings = profileSettings;
        }

        protected String targetUuid(MenuRenderContext<T> context) {
            return context.route() != null ? context.route().param("targetUuid") : null;
        }

        protected PlayerData resolveTargetData(MenuRenderContext<T> context) {
            return resolveTarget(profileSettings, context.session(), targetUuid(context));
        }

        protected void targetAction(String id, BiConsumer<MenuRenderContext<T>, PlayerData> handler) {
            action(id, ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData != null) {
                    handler.accept(ctx, targetData);
                }
            });
        }

        protected void targetActionPrefix(String prefix, TriConsumer<MenuRenderContext<T>, PlayerData, String> handler) {
            actionPrefix(prefix, (ctx, suffix) -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData != null) {
                    handler.accept(ctx, targetData, suffix);
                }
            });
        }

        /**
         * Renders the flow with shared target resolution and access gating.
         * Sends "error-player-not-found" / "error-no-access" to the client and returns
         * a close-only error screen when the target is missing or the viewer lacks access;
         * otherwise delegates to the renderer with the resolved target data and locale.
         */
        protected MenuScreen renderGuarded(MenuRenderContext<T> context, String titleKey, BiFunction<PlayerData, Localization, MenuScreen> renderer) {
            Session session = context.session();
            PlayerData targetData = resolveTargetData(context);

            if (targetData == null) {
                session.locale().send("error-player-not-found");
                return errorScreen(session, titleKey, "error-player-not-found");
            }

            if (!hasAccess(session, targetData)) {
                session.locale().send("error-no-access");
                return errorScreen(session, titleKey, "error-no-access");
            }

            return renderer.apply(targetData, context.locale());
        }
    }

    @FunctionalInterface
    interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    static final class SettingsFlow extends BasePlayerSettingsFlow<SettingsState> {

        SettingsFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_SETTINGS, SettingsState.class, profileSettings);

            targetAction("custom-nickname", (ctx, targetData) -> {
                ctx.openPrompt(new MenuPrompt(
                        "custom-nickname",
                        ctx.locale().t("player-menu-settings-customNickname-title"),
                        ctx.locale().t("player-menu-settings-customNickname-message"),
                        256,
                        targetData.customNickname,
                        false
                ));
            });

            targetAction("custom-nickname-reset", (ctx, targetData) -> {
                profileSettings.updateCustomNickname(targetData, "", true, true);
                ctx.render();
            });

            targetAction("description", (ctx, targetData) -> {
                ctx.openPrompt(new MenuPrompt(
                        "description",
                        ctx.locale().t("player-menu-settings-description-title"),
                        "",
                        1000,
                        targetData.description,
                        false
                ));
            });

            action("chat-settings", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_CHAT_SETTINGS).withParam("targetUuid", targetUuid(ctx))));
            action("badges", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_BADGES).withParam("targetUuid", targetUuid(ctx))));

            targetAction("leaderboard", (ctx, targetData) -> {
                profileSettings.updateLeaderboard(targetData, !targetData.leaderboard);
                ctx.render();
            });

            action("language", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_LANGUAGE_SELECTION)
                    .withParam("targetUuid", targetUuid(ctx))
                    .withParam("isTranslator", "false")));

            onPrompt("custom-nickname",
                    ctx -> {
                        PlayerData targetData = resolveTargetData(ctx.renderContext());
                        if (targetData == null) return;
                        String newNick = ctx.text() == null || ctx.text().trim().isEmpty() ? "" : ctx.text().trim();
                        if (!newNick.isEmpty()) {
                            var result = profileSettings.validateCustomNickname(newNick);
                            if (!result.valid()) {
                                ctx.renderContext().locale().send(result.errorKey(), args("max", result.maxBytes()));
                                ctx.renderContext().render();
                                return;
                            }
                        }
                        profileSettings.updateCustomNickname(targetData, newNick, true, true);
                        ctx.renderContext().render();
                    },
                    MenuRenderContext::render
            );

            onPrompt("description",
                    ctx -> {
                        PlayerData targetData = resolveTargetData(ctx.renderContext());
                        if (targetData == null) return;
                        profileSettings.updateDescription(targetData, ctx.text());
                        ctx.renderContext().render();
                    },
                    MenuRenderContext::render
            );
        }

        @Override
        protected String targetUuid(MenuRenderContext<SettingsState> context) {
            return context.state() != null && context.state().targetUuid != null
                    ? context.state().targetUuid
                    : super.targetUuid(context);
        }

        @Override
        protected PlayerData resolveTargetData(MenuRenderContext<SettingsState> context) {
            SettingsState state = context.state();
            if (state != null && state.targetData != null) {
                return state.targetData;
            }
            return super.resolveTargetData(context);
        }

        @Override
        public SettingsState createState(Session session, MenuRoute route, SettingsState currentState) {
            String targetUuid = route.param("targetUuid");
            if (currentState != null && Objects.equals(currentState.targetUuid, targetUuid)) {
                return currentState;
            }
            return new SettingsState(targetUuid);
        }

        @Override
        public MenuScreen render(MenuRenderContext<SettingsState> context) {
            return renderGuarded(context, "player-menu-settings-title", (targetData, local) -> {
                String displayNickname = (targetData.customNickname == null || targetData.customNickname.isEmpty())
                        ? targetData.nickname : targetData.customNickname;
                String customNickDisplay = (targetData.customNickname == null || targetData.customNickname.isEmpty())
                        ? local.t("none") : targetData.customNickname;
                String descDisplay = (targetData.description == null || targetData.description.isEmpty())
                        ? local.t("no-description") : targetData.description;
                String activeBadge = activeBadgeName(local, targetData);
                String systemBadge = systemBadgeName(local, targetData);
                String globalChat = targetData.globalChatVisible ? local.t("yes") : local.t("no");
                String discordRelay = targetData.discordRelayVisible ? local.t("yes") : local.t("no");

                var grid = new MenuGrid();
                grid.row(
                        MenuButton.of(local.t("player-menu-settings-customNickname"), "custom-nickname"),
                        MenuButton.of(local.t("player-menu-settings-customNickname-reset"), "custom-nickname-reset"),
                        MenuButton.of(local.t("player-menu-settings-description"), "description")
                );
                grid.row(
                        MenuButton.of(local.t("player-menu-settings-chat"), "chat-settings"),
                        MenuButton.of(local.t("player-menu-settings-badges"), "badges")
                );
                grid.row(MenuButton.of(
                        local.t(targetData.leaderboard ? "player-leaderboard-active" : "player-leaderboard-inactive"),
                        "leaderboard"));
                grid.row(MenuButton.of(
                        local.t("settings-language-label", args("lang", local.getLanguageName(targetData.language, "auto"))),
                        "language"));
                grid.defaultNavigation(context);

                return MenuScreen.normal(
                        local.t("player-menu-settings-title"),
                        local.t("player-menu-settings-content", args(
                                "displayNickname", displayNickname,
                                "pid", targetData.pid,
                                "nickname", targetData.nickname,
                                "customNickname", customNickDisplay,
                                "activeBadge", activeBadge,
                                "systemBadge", systemBadge,
                                "description", descDisplay,
                                "leaderboard", targetData.leaderboard ? local.t("yes") : local.t("no"),
                                "language", local.getLanguageName(targetData.language, "auto"),
                                "translatorLanguage", local.getLanguageName(targetData.translatorLanguage, "off"),
                                "globalChat", globalChat,
                                "discordRelay", discordRelay
                        )),
                        grid.build()
                );
            });
        }
    }

    static final class ChatSettingsFlow extends BasePlayerSettingsFlow<ChatSettingsState> {

        ChatSettingsFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_CHAT_SETTINGS, ChatSettingsState.class, profileSettings);

            targetAction("toggle-global-chat", (ctx, targetData) -> {
                profileSettings.updateGlobalChatVisible(targetData, !targetData.globalChatVisible);
                ctx.render();
            });
            targetAction("toggle-discord-relay", (ctx, targetData) -> {
                profileSettings.updateDiscordRelayVisible(targetData, !targetData.discordRelayVisible);
                ctx.render();
            });
            action("translator-language", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_LANGUAGE_SELECTION)
                    .withParam("targetUuid", targetUuid(ctx))
                    .withParam("isTranslator", "true")));
        }

        @Override
        public ChatSettingsState createState(Session session, MenuRoute route, ChatSettingsState currentState) {
            String targetUuid = route.param("targetUuid");
            if (currentState != null && Objects.equals(currentState.targetUuid, targetUuid)) {
                return currentState;
            }
            return new ChatSettingsState(targetUuid);
        }

        @Override
        public MenuScreen render(MenuRenderContext<ChatSettingsState> context) {
            return renderGuarded(context, "player-menu-settings-chat-title", (targetData, local) -> {
                var grid = new MenuGrid();
                grid.row(MenuButton.of(
                        local.t(targetData.globalChatVisible ? "player-menu-settings-global-chat-on" : "player-menu-settings-global-chat-off"),
                        "toggle-global-chat"));
                grid.row(MenuButton.of(
                        local.t(targetData.discordRelayVisible ? "player-menu-settings-discord-relay-on" : "player-menu-settings-discord-relay-off"),
                        "toggle-discord-relay"));
                grid.row(MenuButton.of(
                        local.t("settings-translator-label", args("lang", local.getLanguageName(targetData.translatorLanguage, "off"))),
                        "translator-language"));
                grid.defaultNavigation(context);

                return MenuScreen.normal(
                        local.t("player-menu-settings-chat-title"),
                        local.t("player-menu-settings-chat-content", args(
                                "globalChat", targetData.globalChatVisible ? local.t("yes") : local.t("no"),
                                "discordRelay", targetData.discordRelayVisible ? local.t("yes") : local.t("no"),
                                "translatorLanguage", local.getLanguageName(targetData.translatorLanguage, "off")
                        )),
                        grid.build()
                );
            });
        }
    }

    static final class LanguageSelectionFlow extends BasePlayerSettingsFlow<LanguageSelectionState> {
        private final Bundle bundle;

        LanguageSelectionFlow(Bundle bundle, PlayerProfileSettingsService profileSettings) {
            super(ROUTE_LANGUAGE_SELECTION, LanguageSelectionState.class, profileSettings);
            this.bundle = bundle;

            targetAction("auto", (ctx, targetData) -> {
                profileSettings.updateLanguage(targetData, "auto");
                ctx.goBack();
            });
            targetAction("default", (ctx, targetData) -> {
                profileSettings.updateTranslatorLanguage(targetData, "off");
                ctx.goBack();
            });
            targetActionPrefix(ACTION_LANGUAGE_PREFIX, (ctx, targetData, languageCode) -> {
                if (ctx.state().isTranslator) {
                    profileSettings.updateTranslatorLanguage(targetData, languageCode);
                } else {
                    profileSettings.updateLanguage(targetData, languageCode);
                }
                ctx.goBack();
            });
        }

        @Override
        public LanguageSelectionState createState(Session session, MenuRoute route, LanguageSelectionState currentState) {
            String targetUuid = route.param("targetUuid");
            boolean isTranslator = Boolean.parseBoolean(route.param("isTranslator"));
            if (currentState != null && Objects.equals(currentState.targetUuid, targetUuid) && currentState.isTranslator == isTranslator) {
                return currentState;
            }
            return new LanguageSelectionState(targetUuid, isTranslator);
        }

        @Override
        public MenuScreen render(MenuRenderContext<LanguageSelectionState> context) {
            LanguageSelectionState state = context.state();
            String titleKey = state.isTranslator ? "player-menu-settings-translator-title" : "player-menu-settings-language-title";

            return renderGuarded(context, titleKey, (targetData, local) -> {
                Seq<Locale> locales = bundle.getAvailableLocales();
                var grid = new MenuGrid();
                String firstActionId = state.isTranslator ? "default" : "auto";
                String firstLabelKey = state.isTranslator ? "default" : "auto";
                grid.row(MenuButton.of(local.t(firstLabelKey), firstActionId));

                for (Locale loc : locales) {
                    String code = "uk".equals(loc.getLanguage()) ? "uk_UA" : loc.getLanguage();
                    String langName = Strings.capitalize(loc.getDisplayLanguage(loc));
                    grid.row(MenuButton.of(langName, ACTION_LANGUAGE_PREFIX + code));
                }

                grid.defaultNavigation(context);
                return MenuScreen.normal(local.t(titleKey), "", grid.build());
            });
        }
    }

    static final class BadgeSymbolColorModeFlow extends BasePlayerSettingsFlow<BadgeSymbolColorModeState> {

        BadgeSymbolColorModeFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_BADGE_SYMBOL_COLOR, BadgeSymbolColorModeState.class, profileSettings);

            targetAction("set-default-mode", (ctx, targetData) -> {
                profileSettings.updateBadgeSymbolColorMode(targetData, "default", true, true);
                ctx.render();
            });
            targetAction("set-player-color-mode", (ctx, targetData) -> {
                profileSettings.updateBadgeSymbolColorMode(targetData, "player-color", true, true);
                ctx.render();
            });
        }

        @Override
        public BadgeSymbolColorModeState createState(Session session, MenuRoute route, BadgeSymbolColorModeState currentState) {
            String targetUuid = route.param("targetUuid");
            if (currentState != null && Objects.equals(currentState.targetUuid, targetUuid)) {
                return currentState;
            }
            return new BadgeSymbolColorModeState(targetUuid);
        }

        @Override
        public MenuScreen render(MenuRenderContext<BadgeSymbolColorModeState> context) {
            return renderGuarded(context, "badge-menu-symbol-color-title", (targetData, local) -> {
                var grid = new MenuGrid();
                grid.row(MenuButton.of(local.t("badge-menu-symbol-color-default"), "set-default-mode"));
                grid.row(MenuButton.of(local.t("badge-menu-symbol-color-player-color"), "set-player-color-mode"));
                grid.defaultNavigation(context);

                return MenuScreen.normal(
                        local.t("badge-menu-symbol-color-title"),
                        local.t("badge-menu-symbol-color-content", args("mode", badgeSymbolColorModeLabel(local, targetData))),
                        grid.build()
                );
            });
        }
    }

    static final class BadgesFlow extends BasePlayerSettingsFlow<BadgesState> {

        BadgesFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_BADGES, BadgesState.class, profileSettings);

            action("symbol-color-mode", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_BADGE_SYMBOL_COLOR).withParam("targetUuid", targetUuid(ctx))));
            action("view-all", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_ALL_BADGES).withParam("targetUuid", targetUuid(ctx))));

            targetAction("clear", (ctx, targetData) -> {
                profileSettings.updateActiveBadge(targetData, "", true, true);
                ctx.render();
            });

            targetActionPrefix(ACTION_BADGE_PREFIX, (ctx, targetData, badgeId) -> {
                Badge badge = Badge.byId(badgeId);
                if (badge != null) {
                    profileSettings.updateActiveBadge(targetData, badge.id(), true, true);
                    ctx.render();
                }
            });
        }

        @Override
        public BadgesState createState(Session session, MenuRoute route, BadgesState currentState) {
            String targetUuid = route.param("targetUuid");
            if (currentState != null && Objects.equals(currentState.targetUuid, targetUuid)) {
                return currentState;
            }
            return new BadgesState(targetUuid);
        }

        @Override
        public MenuScreen render(MenuRenderContext<BadgesState> context) {
            return renderGuarded(context, "badge-menu-title", (targetData, local) -> {
                List<Badge> badges = unlockedSelectableBadges(targetData);
                String header = local.t("badge-menu-content", args(
                        "systemBadge", systemBadgeName(local, targetData),
                        "activeBadge", activeBadgeName(local, targetData),
                        "symbolColorMode", badgeSymbolColorModeLabel(local, targetData)
                ));

                var grid = new MenuGrid();
                for (Badge badge : badges) {
                    grid.row(MenuButton.of(
                            local.t("badge-menu-row", args(
                                    "badge", badgeLabel(local, badge),
                                    "description", local.t(badge.descriptionKey())
                            )),
                            ACTION_BADGE_PREFIX + badge.id()));
                }

                grid.row(MenuButton.of(
                        local.t("badge-menu-symbol-color-button", args("mode", badgeSymbolColorModeLabel(local, targetData))),
                        "symbol-color-mode"));

                grid.row(
                        MenuButton.of(local.t("badge-menu-view-all"), "view-all"),
                        MenuButton.of(local.t("badge-clear-button"), "clear")
                );

                grid.defaultNavigation(context);

                return MenuScreen.normal(
                        local.t("badge-menu-title"),
                        badges.isEmpty() ? header + "\n" + local.t("badge-menu-empty") : header,
                        grid.build()
                );
            });
        }
    }

    static final class AllBadgesFlow extends BasePlayerSettingsFlow<AllBadgesState> {

        AllBadgesFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_ALL_BADGES, AllBadgesState.class, profileSettings);

            targetActionPrefix(ACTION_BADGE_PREFIX, (ctx, targetData, badgeId) -> {
                Badge badge = Badge.byId(badgeId);
                if (badge != null) {
                    if (badge.selectable() && !badge.system() && ownsBadge(targetData, badge)) {
                        profileSettings.updateActiveBadge(targetData, badge.id(), true, true);
                    }
                    ctx.render();
                }
            });
        }

        @Override
        public AllBadgesState createState(Session session, MenuRoute route, AllBadgesState currentState) {
            String targetUuid = route.param("targetUuid");
            if (currentState != null && Objects.equals(currentState.targetUuid, targetUuid)) {
                return currentState;
            }
            return new AllBadgesState(targetUuid);
        }

        @Override
        public MenuScreen render(MenuRenderContext<AllBadgesState> context) {
            return renderGuarded(context, "badge-menu-all-title", (targetData, local) -> {
                var grid = new MenuGrid();
                for (Badge badge : Badge.values()) {
                    grid.row(MenuButton.of(
                            local.t("badge-menu-all-row", args(
                                    "badge", badgeLabel(local, badge),
                                    "state", badgeState(local, targetData, badge),
                                    "description", local.t(badge.descriptionKey())
                            )),
                            ACTION_BADGE_PREFIX + badge.id()));
                }

                grid.defaultNavigation(context);

                return MenuScreen.normal(
                        local.t("badge-menu-all-title"),
                        local.t("badge-menu-all-content"),
                        grid.build()
                );
            });
        }
    }

    static final class ChatSettingsState {
        public String targetUuid;

        ChatSettingsState() {}
        ChatSettingsState(String targetUuid) { this.targetUuid = targetUuid; }
    }

    static final class BadgeSymbolColorModeState {
        public String targetUuid;

        BadgeSymbolColorModeState() {}
        BadgeSymbolColorModeState(String targetUuid) { this.targetUuid = targetUuid; }
    }

    static final class BadgesState {
        public String targetUuid;

        BadgesState() {}
        BadgesState(String targetUuid) { this.targetUuid = targetUuid; }
    }

    static final class AllBadgesState {
        public String targetUuid;

        AllBadgesState() {}
        AllBadgesState(String targetUuid) { this.targetUuid = targetUuid; }
    }

    static final class LanguageSelectionState {
        public String targetUuid;
        public boolean isTranslator;

        LanguageSelectionState() {}
        LanguageSelectionState(String targetUuid, boolean isTranslator) {
            this.targetUuid = targetUuid;
            this.isTranslator = isTranslator;
        }
    }

    static final class SettingsState {
        public String targetUuid;
        public PlayerData targetData;

        SettingsState() {}
        SettingsState(String targetUuid) { this.targetUuid = targetUuid; }
        SettingsState(String targetUuid, PlayerData targetData) {
            this.targetUuid = targetUuid;
            this.targetData = targetData;
        }
    }

    private static MenuScreen errorScreen(Session session, String titleKey, String messageKey) {
        Localization local = session.locale();
        return MenuScreen.normal(
                local.t(titleKey),
                local.t(messageKey),
                MenuGrid.onlyClose(local)
        );
    }

    private static boolean hasAccess(Session session, PlayerData targetData) {
        if (session == null || targetData == null) return false;
        return (session.player != null && session.player.admin)
                || (session.data != null && Objects.equals(session.data.uuid, targetData.uuid));
    }

    static String activeBadgeName(Localization local, PlayerData targetData) {
        Badge badge = Badge.byId(targetData.activeBadge);
        if (badge == null || targetData.unlockedBadges == null || !targetData.unlockedBadges.contains(badge.id())) {
            return local.t("none");
        }
        return badgeLabel(local, badge);
    }

    static String systemBadgeName(Localization local, PlayerData targetData) {
        return targetData.admin ? badgeLabel(local, Badge.ADMIN) : local.t("none");
    }

    static String badgeSymbolColorModeLabel(Localization local, PlayerData targetData) {
        return usesPlayerBadgeSymbolColor(targetData)
                ? local.t("badge-menu-symbol-color-player-color")
                : local.t("badge-menu-symbol-color-default");
    }

    static List<Badge> unlockedSelectableBadges(PlayerData targetData) {
        List<Badge> result = new ArrayList<>();
        for (Badge badge : Badge.selectableManualBadges()) {
            if (targetData.unlockedBadges != null && targetData.unlockedBadges.contains(badge.id())) {
                result.add(badge);
            }
        }
        return result;
    }

    static String badgeLabel(Localization local, Badge badge) {
        return badge.tag() + " " + local.t(badge.nameKey());
    }

    static String badgeState(Localization local, PlayerData targetData, Badge badge) {
        if (badge.system()) {
            return targetData.admin ? local.t("badge-state-system-active") : local.t("badge-state-system");
        }

        if (badge.id().equals(targetData.activeBadge) && ownsBadge(targetData, badge)) {
            return local.t("badge-state-active");
        }

        return ownsBadge(targetData, badge) ? local.t("badge-state-unlocked") : local.t("badge-state-locked");
    }

    static boolean ownsBadge(PlayerData targetData, Badge badge) {
        return targetData.unlockedBadges != null && targetData.unlockedBadges.contains(badge.id());
    }

    private static boolean usesPlayerBadgeSymbolColor(PlayerData targetData) {
        return targetData != null
                && targetData.badgeSymbolColorMode != null
                && targetData.badgeSymbolColorMode.equalsIgnoreCase("player-color");
    }
}
