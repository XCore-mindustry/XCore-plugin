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

    static final class SettingsFlow extends BaseMenuFlow<SettingsState> {
        private final PlayerProfileSettingsService profileSettings;

        SettingsFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_SETTINGS, SettingsState.class);
            this.profileSettings = profileSettings;

            action("custom-nickname", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                ctx.openPrompt(new MenuPrompt(
                        "custom-nickname",
                        ctx.locale().t("player-menu-settings-customNickname-title"),
                        ctx.locale().t("player-menu-settings-customNickname-message"),
                        256,
                        targetData.customNickname,
                        false
                ));
            });
            action("custom-nickname-reset", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                profileSettings.updateCustomNickname(targetData, "", true, true);
                ctx.render();
            });
            action("description", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                ctx.openPrompt(new MenuPrompt(
                        "description",
                        ctx.locale().t("player-menu-settings-description-title"),
                        "",
                        1000,
                        targetData.description,
                        false
                ));
            });
            action("chat-settings", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_CHAT_SETTINGS).withParam("targetUuid", ctx.state().targetUuid)));
            action("badges", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_BADGES).withParam("targetUuid", ctx.state().targetUuid)));
            action("leaderboard", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                profileSettings.updateLeaderboard(targetData, !targetData.leaderboard);
                ctx.render();
            });
            action("language", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_LANGUAGE_SELECTION)
                    .withParam("targetUuid", ctx.state().targetUuid)
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
                    ctx -> ctx.render()
            );
            onPrompt("description",
                    ctx -> {
                        PlayerData targetData = resolveTargetData(ctx.renderContext());
                        if (targetData == null) return;
                        profileSettings.updateDescription(targetData, ctx.text());
                        ctx.renderContext().render();
                    },
                    ctx -> ctx.render()
            );
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
            Session session = context.session();
            SettingsState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);

            if (targetData == null) {
                session.locale().send("error-player-not-found");
                return errorScreen(session, "player-menu-settings-title", "error-player-not-found");
            }

            if (!hasAccess(session, targetData)) {
                session.locale().send("error-no-access");
                return errorScreen(session, "player-menu-settings-title", "error-no-access");
            }

            Localization local = context.locale();

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
            grid.defaultNavigation(session, local);

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
        }

        private PlayerData resolveTargetData(MenuRenderContext<SettingsState> context) {
            return context.session().playerDataRepository.findByUuid(context.state().targetUuid);
        }
    }

    static final class ChatSettingsFlow extends BaseMenuFlow<ChatSettingsState> {
        private final PlayerProfileSettingsService profileSettings;

        ChatSettingsFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_CHAT_SETTINGS, ChatSettingsState.class);
            this.profileSettings = profileSettings;

            action("toggle-global-chat", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                profileSettings.updateGlobalChatVisible(targetData, !targetData.globalChatVisible);
                ctx.render();
            });
            action("toggle-discord-relay", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                profileSettings.updateDiscordRelayVisible(targetData, !targetData.discordRelayVisible);
                ctx.render();
            });
            action("translator-language", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_LANGUAGE_SELECTION)
                    .withParam("targetUuid", ctx.state().targetUuid)
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
            Session session = context.session();
            ChatSettingsState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);

            if (targetData == null) {
                session.locale().send("error-player-not-found");
                return errorScreen(session, "player-menu-settings-chat-title", "error-player-not-found");
            }

            if (!hasAccess(session, targetData)) {
                session.locale().send("error-no-access");
                return errorScreen(session, "player-menu-settings-chat-title", "error-no-access");
            }

            Localization local = context.locale();

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
            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("player-menu-settings-chat-title"),
                    local.t("player-menu-settings-chat-content", args(
                            "globalChat", targetData.globalChatVisible ? local.t("yes") : local.t("no"),
                            "discordRelay", targetData.discordRelayVisible ? local.t("yes") : local.t("no"),
                            "translatorLanguage", local.getLanguageName(targetData.translatorLanguage, "off")
                    )),
                    grid.build()
            );
        }

        private PlayerData resolveTargetData(MenuRenderContext<ChatSettingsState> context) {
            return context.session().playerDataRepository.findByUuid(context.state().targetUuid);
        }
    }

    static final class LanguageSelectionFlow extends BaseMenuFlow<LanguageSelectionState> {
        private final Bundle bundle;
        private final PlayerProfileSettingsService profileSettings;

        LanguageSelectionFlow(Bundle bundle, PlayerProfileSettingsService profileSettings) {
            super(ROUTE_LANGUAGE_SELECTION, LanguageSelectionState.class);
            this.bundle = bundle;
            this.profileSettings = profileSettings;

            action("auto", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                profileSettings.updateLanguage(targetData, "auto");
                ctx.goBack();
            });
            action("default", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                profileSettings.updateTranslatorLanguage(targetData, "off");
                ctx.goBack();
            });
            actionPrefix(ACTION_LANGUAGE_PREFIX, (ctx, languageCode) -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
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
            Session session = context.session();
            LanguageSelectionState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);

            String titleKey = state.isTranslator ? "player-menu-settings-translator-title" : "player-menu-settings-language-title";

            if (targetData == null) {
                session.locale().send("error-player-not-found");
                return errorScreen(session, titleKey, "error-player-not-found");
            }

            if (!hasAccess(session, targetData)) {
                session.locale().send("error-no-access");
                return errorScreen(session, titleKey, "error-no-access");
            }

            Localization local = context.locale();
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

            grid.defaultNavigation(session, local);

            return MenuScreen.normal(local.t(titleKey), "", grid.build());
        }

        private PlayerData resolveTargetData(MenuRenderContext<LanguageSelectionState> context) {
            return context.session().playerDataRepository.findByUuid(context.state().targetUuid);
        }
    }

    static final class BadgeSymbolColorModeFlow extends BaseMenuFlow<BadgeSymbolColorModeState> {
        private final PlayerProfileSettingsService profileSettings;

        BadgeSymbolColorModeFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_BADGE_SYMBOL_COLOR, BadgeSymbolColorModeState.class);
            this.profileSettings = profileSettings;

            action("set-default-mode", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                profileSettings.updateBadgeSymbolColorMode(targetData, "default", true, true);
                ctx.render();
            });
            action("set-player-color-mode", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
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
            Session session = context.session();
            BadgeSymbolColorModeState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);

            if (targetData == null) {
                session.locale().send("error-player-not-found");
                return errorScreen(session, "badge-menu-symbol-color-title", "error-player-not-found");
            }

            if (!hasAccess(session, targetData)) {
                session.locale().send("error-no-access");
                return errorScreen(session, "badge-menu-symbol-color-title", "error-no-access");
            }

            Localization local = context.locale();

            var grid = new MenuGrid();
            grid.row(MenuButton.of(local.t("badge-menu-symbol-color-default"), "set-default-mode"));
            grid.row(MenuButton.of(local.t("badge-menu-symbol-color-player-color"), "set-player-color-mode"));
            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("badge-menu-symbol-color-title"),
                    local.t("badge-menu-symbol-color-content", args("mode", badgeSymbolColorModeLabel(local, targetData))),
                    grid.build()
            );
        }

        private PlayerData resolveTargetData(MenuRenderContext<BadgeSymbolColorModeState> context) {
            return context.session().playerDataRepository.findByUuid(context.state().targetUuid);
        }
    }

    static final class BadgesFlow extends BaseMenuFlow<BadgesState> {
        private final PlayerProfileSettingsService profileSettings;

        BadgesFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_BADGES, BadgesState.class);
            this.profileSettings = profileSettings;

            action("symbol-color-mode", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_BADGE_SYMBOL_COLOR).withParam("targetUuid", ctx.state().targetUuid)));
            action("view-all", ctx -> ctx.openRoute(MenuRoute.of(ROUTE_ALL_BADGES).withParam("targetUuid", ctx.state().targetUuid)));
            action("clear", ctx -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
                profileSettings.updateActiveBadge(targetData, "", true, true);
                ctx.render();
            });
            actionPrefix(ACTION_BADGE_PREFIX, (ctx, badgeId) -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
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
            Session session = context.session();
            BadgesState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);

            if (targetData == null) {
                session.locale().send("error-player-not-found");
                return errorScreen(session, "badge-menu-title", "error-player-not-found");
            }

            if (!hasAccess(session, targetData)) {
                session.locale().send("error-no-access");
                return errorScreen(session, "badge-menu-title", "error-no-access");
            }

            Localization local = context.locale();
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

            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("badge-menu-title"),
                    badges.isEmpty() ? header + "\n" + local.t("badge-menu-empty") : header,
                    grid.build()
            );
        }

        private PlayerData resolveTargetData(MenuRenderContext<BadgesState> context) {
            return context.session().playerDataRepository.findByUuid(context.state().targetUuid);
        }
    }

    static final class AllBadgesFlow extends BaseMenuFlow<AllBadgesState> {
        private final PlayerProfileSettingsService profileSettings;

        AllBadgesFlow(PlayerProfileSettingsService profileSettings) {
            super(ROUTE_ALL_BADGES, AllBadgesState.class);
            this.profileSettings = profileSettings;

            actionPrefix(ACTION_BADGE_PREFIX, (ctx, badgeId) -> {
                PlayerData targetData = resolveTargetData(ctx);
                if (targetData == null) return;
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
            Session session = context.session();
            AllBadgesState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);

            if (targetData == null) {
                session.locale().send("error-player-not-found");
                return errorScreen(session, "badge-menu-all-title", "error-player-not-found");
            }

            if (!hasAccess(session, targetData)) {
                session.locale().send("error-no-access");
                return errorScreen(session, "badge-menu-all-title", "error-no-access");
            }

            Localization local = context.locale();
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

            grid.defaultNavigation(session, local);

            return MenuScreen.normal(
                    local.t("badge-menu-all-title"),
                    local.t("badge-menu-all-content"),
                    grid.build()
            );
        }

        private PlayerData resolveTargetData(MenuRenderContext<AllBadgesState> context) {
            return context.session().playerDataRepository.findByUuid(context.state().targetUuid);
        }
    }

    static final class ChatSettingsState {
        public String targetUuid;

        ChatSettingsState() {
        }

        ChatSettingsState(String targetUuid) {
            this.targetUuid = targetUuid;
        }
    }

    static final class BadgeSymbolColorModeState {
        public String targetUuid;

        BadgeSymbolColorModeState() {
        }

        BadgeSymbolColorModeState(String targetUuid) {
            this.targetUuid = targetUuid;
        }
    }

    static final class BadgesState {
        public String targetUuid;

        BadgesState() {
        }

        BadgesState(String targetUuid) {
            this.targetUuid = targetUuid;
        }
    }

    static final class AllBadgesState {
        public String targetUuid;

        AllBadgesState() {
        }

        AllBadgesState(String targetUuid) {
            this.targetUuid = targetUuid;
        }
    }

    static final class LanguageSelectionState {
        public String targetUuid;
        public boolean isTranslator;

        LanguageSelectionState() {
        }

        LanguageSelectionState(String targetUuid, boolean isTranslator) {
            this.targetUuid = targetUuid;
            this.isTranslator = isTranslator;
        }
    }

    static final class SettingsState {
        public String targetUuid;

        SettingsState() {
        }

        SettingsState(String targetUuid) {
            this.targetUuid = targetUuid;
        }
    }

    private static MenuScreen errorScreen(Session session, String titleKey, String messageKey) {
        Localization local = session.locale();
        return MenuScreen.normal(
                local.t(titleKey),
                local.t(messageKey),
                new MenuGrid().row(MenuButton.of(local.t("close"), "close")).build()
        );
    }

    private static boolean hasAccess(Session session, PlayerData targetData) {
        return session.data.uuid.equals(targetData.uuid) || session.player.admin;
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
