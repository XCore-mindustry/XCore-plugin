package org.xcore.plugin.ui.menu;

import arc.struct.Seq;
import arc.util.Strings;
import com.ospx.flubundle.Bundle;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.flow.MenuButton;
import org.xcore.plugin.ui.flow.MenuPrompt;
import org.xcore.plugin.ui.flow.MenuRenderContext;
import org.xcore.plugin.ui.flow.MenuScreen;
import org.xcore.plugin.ui.route.MenuRoute;
import org.xcore.plugin.ui.route.RoutedMenuFlow;

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

    private static final String ACTION_TOGGLE_GLOBAL_CHAT = "toggle-global-chat";
    private static final String ACTION_TOGGLE_DISCORD_RELAY = "toggle-discord-relay";
    private static final String ACTION_TRANSLATOR_LANGUAGE = "translator-language";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_SET_DEFAULT_MODE = "set-default-mode";
    private static final String ACTION_SET_PLAYER_COLOR_MODE = "set-player-color-mode";
    private static final String ACTION_SYMBOL_COLOR_MODE = "symbol-color-mode";
    private static final String ACTION_VIEW_ALL = "view-all";
    private static final String ACTION_CLEAR = "clear";
    private static final String ACTION_CUSTOM_NICKNAME = "custom-nickname";
    private static final String ACTION_CUSTOM_NICKNAME_RESET = "custom-nickname-reset";
    private static final String ACTION_DESCRIPTION = "description";
    private static final String ACTION_CHAT_SETTINGS = "chat-settings";
    private static final String ACTION_BADGES = "badges";
    private static final String ACTION_LEADERBOARD = "leaderboard";
    private static final String ACTION_LANGUAGE = "language";
    private static final String ACTION_SELECT_AUTO = "auto";
    private static final String ACTION_SELECT_DEFAULT = "default";

    private PlayerSettingsFlows() {
    }

    static final class SettingsFlow implements RoutedMenuFlow<SettingsState> {
        private final PlayerProfileSettingsService profileSettings;

        SettingsFlow(PlayerProfileSettingsService profileSettings) {
            this.profileSettings = profileSettings;
        }

        @Override
        public String routeId() {
            return ROUTE_SETTINGS;
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
        public Class<SettingsState> stateType() {
            return SettingsState.class;
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

            String customNickDisplay = (targetData.customNickname == null || targetData.customNickname.isEmpty())
                    ? local.t("none") : targetData.customNickname;
            String descDisplay = (targetData.description == null || targetData.description.isEmpty())
                    ? local.t("no-description") : targetData.description;
            String activeBadge = activeBadgeName(local, targetData);
            String systemBadge = systemBadgeName(local, targetData);
            String globalChat = targetData.globalChatVisible ? local.t("yes") : local.t("no");
            String discordRelay = targetData.discordRelayVisible ? local.t("yes") : local.t("no");

            List<List<MenuButton>> rows = new ArrayList<>();

            rows.add(List.of(
                    MenuButton.of(local.t("player-menu-settings-customNickname"), ACTION_CUSTOM_NICKNAME),
                    MenuButton.of(local.t("player-menu-settings-customNickname-reset"), ACTION_CUSTOM_NICKNAME_RESET),
                    MenuButton.of(local.t("player-menu-settings-description"), ACTION_DESCRIPTION)));

            rows.add(List.of(MenuButton.of(
                    local.t("player-menu-settings-chat"),
                    ACTION_CHAT_SETTINGS)));
            rows.add(List.of(MenuButton.of(
                    local.t("player-menu-settings-badges"),
                    ACTION_BADGES)));
            rows.add(List.of(MenuButton.of(
                    local.t(targetData.leaderboard ? "player-leaderboard-active" : "player-leaderboard-inactive"),
                    ACTION_LEADERBOARD)));

            rows.add(List.of(MenuButton.of(
                    local.t("settings-language-label", args("lang", local.getLanguageName(targetData.language, "auto"))),
                    ACTION_LANGUAGE)));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("player-menu-settings-title"),
                    local.t("player-menu-settings-content", args(
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
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<SettingsState> context, String actionId) {
            Session session = context.session();
            SettingsState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);
            if (targetData == null) return;

            Localization local = context.locale();

            switch (actionId) {
                case ACTION_CUSTOM_NICKNAME -> context.openPrompt(new MenuPrompt(
                        ACTION_CUSTOM_NICKNAME,
                        local.t("player-menu-settings-customNickname-title"),
                        local.t("player-menu-settings-customNickname-message"),
                        256,
                        targetData.customNickname,
                        false
                ));
                case ACTION_CUSTOM_NICKNAME_RESET -> {
                    profileSettings.updateCustomNickname(targetData, "", true, true);
                    context.render();
                }
                case ACTION_DESCRIPTION -> context.openPrompt(new MenuPrompt(
                        ACTION_DESCRIPTION,
                        local.t("player-menu-settings-description-title"),
                        "",
                        1000,
                        targetData.description,
                        false
                ));
                case ACTION_CHAT_SETTINGS -> context.openRoute(MenuRoute.of(ROUTE_CHAT_SETTINGS).withParam("targetUuid", state.targetUuid));
                case ACTION_BADGES -> context.openRoute(MenuRoute.of(ROUTE_BADGES).withParam("targetUuid", state.targetUuid));
                case ACTION_LEADERBOARD -> {
                    profileSettings.updateLeaderboard(targetData, !targetData.leaderboard);
                    context.render();
                }
                case ACTION_LANGUAGE -> context.openRoute(MenuRoute.of(ROUTE_LANGUAGE_SELECTION)
                        .withParam("targetUuid", state.targetUuid)
                        .withParam("isTranslator", "false"));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }

        @Override
        public void onPromptSubmit(MenuRenderContext<SettingsState> context, String promptId, String text) {
            Session session = context.session();
            SettingsState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);
            if (targetData == null) return;

            switch (promptId) {
                case ACTION_CUSTOM_NICKNAME -> {
                    String newNick = text == null || text.trim().isEmpty() ? "" : text;
                    if (!newNick.isEmpty()) {
                        var result = profileSettings.validateCustomNickname(newNick);
                        if (!result.valid()) {
                            context.locale().send(result.errorKey(), args("max", result.maxBytes()));
                            context.render();
                            return;
                        }
                    }
                    profileSettings.updateCustomNickname(targetData, newNick, true, true);
                    context.render();
                }
                case ACTION_DESCRIPTION -> {
                    profileSettings.updateDescription(targetData, text);
                    context.render();
                }
                default -> {
                }
            }
        }

        @Override
        public void onPromptCancel(MenuRenderContext<SettingsState> context, String promptId) {
            context.render();
        }
    }

    static final class ChatSettingsFlow implements RoutedMenuFlow<ChatSettingsState> {
        private final PlayerProfileSettingsService profileSettings;

        ChatSettingsFlow(PlayerProfileSettingsService profileSettings) {
            this.profileSettings = profileSettings;
        }

        @Override
        public String routeId() {
            return ROUTE_CHAT_SETTINGS;
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
        public Class<ChatSettingsState> stateType() {
            return ChatSettingsState.class;
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

            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(MenuButton.of(
                    local.t(targetData.globalChatVisible ? "player-menu-settings-global-chat-on" : "player-menu-settings-global-chat-off"),
                    ACTION_TOGGLE_GLOBAL_CHAT)));
            rows.add(List.of(MenuButton.of(
                    local.t(targetData.discordRelayVisible ? "player-menu-settings-discord-relay-on" : "player-menu-settings-discord-relay-off"),
                    ACTION_TOGGLE_DISCORD_RELAY)));
            rows.add(List.of(MenuButton.of(
                    local.t("settings-translator-label", args("lang", local.getLanguageName(targetData.translatorLanguage, "off"))),
                    ACTION_TRANSLATOR_LANGUAGE)));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("player-menu-settings-chat-title"),
                    local.t("player-menu-settings-chat-content", args(
                            "globalChat", targetData.globalChatVisible ? local.t("yes") : local.t("no"),
                            "discordRelay", targetData.discordRelayVisible ? local.t("yes") : local.t("no"),
                            "translatorLanguage", local.getLanguageName(targetData.translatorLanguage, "off")
                    )),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<ChatSettingsState> context, String actionId) {
            Session session = context.session();
            ChatSettingsState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);
            if (targetData == null) return;

            switch (actionId) {
                case ACTION_TOGGLE_GLOBAL_CHAT -> {
                    profileSettings.updateGlobalChatVisible(targetData, !targetData.globalChatVisible);
                    context.render();
                }
                case ACTION_TOGGLE_DISCORD_RELAY -> {
                    profileSettings.updateDiscordRelayVisible(targetData, !targetData.discordRelayVisible);
                    context.render();
                }
                case ACTION_TRANSLATOR_LANGUAGE -> context.openRoute(MenuRoute.of(ROUTE_LANGUAGE_SELECTION)
                        .withParam("targetUuid", state.targetUuid)
                        .withParam("isTranslator", "true"));
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }
    }

    static final class LanguageSelectionFlow implements RoutedMenuFlow<LanguageSelectionState> {
        private final Bundle bundle;
        private final PlayerProfileSettingsService profileSettings;

        LanguageSelectionFlow(Bundle bundle, PlayerProfileSettingsService profileSettings) {
            this.bundle = bundle;
            this.profileSettings = profileSettings;
        }

        @Override
        public String routeId() {
            return ROUTE_LANGUAGE_SELECTION;
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
        public Class<LanguageSelectionState> stateType() {
            return LanguageSelectionState.class;
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

            List<List<MenuButton>> rows = new ArrayList<>();

            String firstActionId = state.isTranslator ? ACTION_SELECT_DEFAULT : ACTION_SELECT_AUTO;
            String firstLabelKey = state.isTranslator ? "default" : "auto";
            rows.add(List.of(MenuButton.of(local.t(firstLabelKey), firstActionId)));

            for (Locale loc : locales) {
                String code = "uk".equals(loc.getLanguage()) ? "uk_UA" : loc.getLanguage();
                String langName = Strings.capitalize(loc.getDisplayLanguage(loc));
                rows.add(List.of(MenuButton.of(langName, code)));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(local.t(titleKey), "", rows);
        }

        @Override
        public void onAction(MenuRenderContext<LanguageSelectionState> context, String actionId) {
            Session session = context.session();
            LanguageSelectionState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);
            if (targetData == null) return;

            switch (actionId) {
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                case ACTION_SELECT_AUTO -> {
                    profileSettings.updateLanguage(targetData, "auto");
                    context.goBack();
                }
                case ACTION_SELECT_DEFAULT -> {
                    profileSettings.updateTranslatorLanguage(targetData, "off");
                    context.goBack();
                }
                default -> {
                    if (state.isTranslator) {
                        profileSettings.updateTranslatorLanguage(targetData, actionId);
                    } else {
                        profileSettings.updateLanguage(targetData, actionId);
                    }
                    context.goBack();
                }
            }
        }
    }

    static final class BadgeSymbolColorModeFlow implements RoutedMenuFlow<BadgeSymbolColorModeState> {
        private final PlayerProfileSettingsService profileSettings;

        BadgeSymbolColorModeFlow(PlayerProfileSettingsService profileSettings) {
            this.profileSettings = profileSettings;
        }

        @Override
        public String routeId() {
            return ROUTE_BADGE_SYMBOL_COLOR;
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
        public Class<BadgeSymbolColorModeState> stateType() {
            return BadgeSymbolColorModeState.class;
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

            List<List<MenuButton>> rows = new ArrayList<>();
            rows.add(List.of(MenuButton.of(local.t("badge-menu-symbol-color-default"), ACTION_SET_DEFAULT_MODE)));
            rows.add(List.of(MenuButton.of(local.t("badge-menu-symbol-color-player-color"), ACTION_SET_PLAYER_COLOR_MODE)));

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("badge-menu-symbol-color-title"),
                    local.t("badge-menu-symbol-color-content", args("mode", badgeSymbolColorModeLabel(local, targetData))),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<BadgeSymbolColorModeState> context, String actionId) {
            Session session = context.session();
            BadgeSymbolColorModeState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);
            if (targetData == null) return;

            switch (actionId) {
                case ACTION_SET_DEFAULT_MODE -> {
                    profileSettings.updateBadgeSymbolColorMode(targetData, "default", true, true);
                    context.render();
                }
                case ACTION_SET_PLAYER_COLOR_MODE -> {
                    profileSettings.updateBadgeSymbolColorMode(targetData, "player-color", true, true);
                    context.render();
                }
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                }
            }
        }
    }

    static final class BadgesFlow implements RoutedMenuFlow<BadgesState> {
        private final PlayerProfileSettingsService profileSettings;

        BadgesFlow(PlayerProfileSettingsService profileSettings) {
            this.profileSettings = profileSettings;
        }

        @Override
        public String routeId() {
            return ROUTE_BADGES;
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
        public Class<BadgesState> stateType() {
            return BadgesState.class;
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

            List<List<MenuButton>> rows = new ArrayList<>();

            for (Badge badge : badges) {
                rows.add(List.of(MenuButton.of(
                        local.t("badge-menu-row", args(
                                "badge", badgeLabel(local, badge),
                                "description", local.t(badge.descriptionKey())
                        )),
                        badge.id())));
            }

            rows.add(List.of(MenuButton.of(
                    local.t("badge-menu-symbol-color-button", args("mode", badgeSymbolColorModeLabel(local, targetData))),
                    ACTION_SYMBOL_COLOR_MODE)));

            List<MenuButton> bottomRow = new ArrayList<>();
            bottomRow.add(MenuButton.of(local.t("badge-menu-view-all"), ACTION_VIEW_ALL));
            bottomRow.add(MenuButton.of(local.t("badge-clear-button"), ACTION_CLEAR));
            rows.add(bottomRow);

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("badge-menu-title"),
                    badges.isEmpty() ? header + "\n" + local.t("badge-menu-empty") : header,
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<BadgesState> context, String actionId) {
            Session session = context.session();
            BadgesState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);
            if (targetData == null) return;

            switch (actionId) {
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                case ACTION_SYMBOL_COLOR_MODE -> context.openRoute(MenuRoute.of(ROUTE_BADGE_SYMBOL_COLOR).withParam("targetUuid", state.targetUuid));
                case ACTION_VIEW_ALL -> context.openRoute(MenuRoute.of(ROUTE_ALL_BADGES).withParam("targetUuid", state.targetUuid));
                case ACTION_CLEAR -> {
                    profileSettings.updateActiveBadge(targetData, "", true, true);
                    context.render();
                }
                default -> {
                    Badge badge = Badge.byId(actionId);
                    if (badge != null) {
                        profileSettings.updateActiveBadge(targetData, badge.id(), true, true);
                        context.render();
                    }
                }
            }
        }
    }

    static final class AllBadgesFlow implements RoutedMenuFlow<AllBadgesState> {
        private final PlayerProfileSettingsService profileSettings;

        AllBadgesFlow(PlayerProfileSettingsService profileSettings) {
            this.profileSettings = profileSettings;
        }

        @Override
        public String routeId() {
            return ROUTE_ALL_BADGES;
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
        public Class<AllBadgesState> stateType() {
            return AllBadgesState.class;
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
            List<List<MenuButton>> rows = new ArrayList<>();

            for (Badge badge : Badge.values()) {
                rows.add(List.of(MenuButton.of(
                        local.t("badge-menu-all-row", args(
                                "badge", badgeLabel(local, badge),
                                "state", badgeState(local, targetData, badge),
                                "description", local.t(badge.descriptionKey())
                        )),
                        badge.id())));
            }

            List<MenuButton> navigation = new ArrayList<>();
            if (session.canGoBack(true)) {
                navigation.add(MenuButton.of(local.t("back"), ACTION_BACK));
            }
            navigation.add(MenuButton.of(local.t("close"), ACTION_CLOSE));
            rows.add(navigation);

            return MenuScreen.normal(
                    local.t("badge-menu-all-title"),
                    local.t("badge-menu-all-content"),
                    rows
            );
        }

        @Override
        public void onAction(MenuRenderContext<AllBadgesState> context, String actionId) {
            Session session = context.session();
            AllBadgesState state = context.state();
            PlayerData targetData = session.playerDataRepository.findByUuid(state.targetUuid);
            if (targetData == null) return;

            switch (actionId) {
                case ACTION_BACK -> context.goBack();
                case ACTION_CLOSE -> context.close();
                default -> {
                    Badge badge = Badge.byId(actionId);
                    if (badge != null) {
                        if (badge.selectable() && !badge.system() && ownsBadge(targetData, badge)) {
                            profileSettings.updateActiveBadge(targetData, badge.id(), true, true);
                        }
                        context.render();
                    }
                }
            }
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
                List.of(List.of(MenuButton.of(local.t("close"), ACTION_CLOSE)))
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
