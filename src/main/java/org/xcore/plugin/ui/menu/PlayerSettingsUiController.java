package org.xcore.plugin.ui.menu;

import arc.util.Strings;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.IdentityDisplayMode;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.responsive.DialogMetrics;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.ospx.flubundle.Bundle.args;

/**
 * Modern reactive player settings dialog utilizing xcore-ui with Elm/MVI architecture.
 */
public class PlayerSettingsUiController implements UiController<PlayerSettingsUiController.SettingsModel, PlayerSettingsUiController.SettingsEvent> {

    public enum Tab {
        PROFILE,
        CHAT_LANG,
        BADGES
    }

    public enum BadgesFilter {
        MY,
        ALL
    }

    public record LanguageOption(String code, String displayName) {}

    public static final List<LanguageOption> AVAILABLE_LANGUAGES = List.of(
            new LanguageOption("auto", "Auto"),
            new LanguageOption("uk_UA", "Українська"),
            new LanguageOption("ru", "Русский"),
            new LanguageOption("en", "English"),
            new LanguageOption("pl", "Polski"),
            new LanguageOption("de", "Deutsch"),
            new LanguageOption("es", "Español"),
            new LanguageOption("fr", "Français"),
            new LanguageOption("be", "Беларуская"),
            new LanguageOption("cs", "Čeština")
    );

    private static final java.util.regex.Pattern HEX_COLOR_PATTERN =
            java.util.regex.Pattern.compile("\\[#([0-9a-fA-F]{6})");

    public record SettingsModel(
            String targetUuid,
            String nickname,
            String username,
            boolean canChangeUsername,
            IdentityDisplayMode identityDisplayMode,
            String customNickname,
            String description,
            boolean globalChatVisible,
            boolean discordRelayVisible,
            boolean leaderboard,
            String activeBadge,
            String previewBadge,
            String language,
            String translatorLanguage,
            String badgeSymbolColorMode,
            Set<String> unlockedBadges,
            boolean isAdmin,
            String playerColorHex,
            Tab tab,
            BadgesFilter badgesFilter,
            String feedbackMessage,
            boolean isSuccess
    ) {
        public SettingsModel withTab(Tab newTab) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, newTab, badgesFilter,
                    "", true);
        }

        public SettingsModel withBadgesFilter(BadgesFilter filter) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, filter,
                    "", true);
        }

        public SettingsModel withUsername(String user) {
            return new SettingsModel(targetUuid, nickname, user, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withCanChangeUsername(boolean canChange) {
            return new SettingsModel(targetUuid, nickname, username, canChange, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withIdentityDisplayMode(IdentityDisplayMode mode) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, mode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withCustomNickname(String nick) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, nick, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withDescription(String desc) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, desc, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withGlobalChatVisible(boolean visible) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, visible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withDiscordRelayVisible(boolean visible) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    visible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withLeaderboard(boolean lb) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, lb, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withActiveBadge(String badge) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, badge, badge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withPreviewBadge(String badge) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, badge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withLanguage(String lang) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, lang, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withTranslatorLanguage(String lang) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, lang,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withBadgeSymbolColorMode(String mode) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    mode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withFeedback(String msg, boolean success) {
            return new SettingsModel(targetUuid, nickname, username, canChangeUsername, identityDisplayMode, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    msg, success);
        }
    }

    public sealed interface SettingsEvent {
        record Save(MenuResult result) implements SettingsEvent {}
        record ResetNickname(MenuResult result) implements SettingsEvent {}
        record SelectTab(Tab tab, MenuResult result) implements SettingsEvent {}
        record SelectBadgesFilter(BadgesFilter filter) implements SettingsEvent {}
        record SelectIdentityDisplayMode(IdentityDisplayMode mode) implements SettingsEvent {}
        record ToggleSymbolColorMode() implements SettingsEvent {}
        record PreviewBadge(String badgeId) implements SettingsEvent {}
        record EquipBadge(String badgeId) implements SettingsEvent {}
        record UnequipBadge() implements SettingsEvent {}
        record SelectLanguage(String code, MenuResult result) implements SettingsEvent {}
        record SelectTranslatorLanguage(String code, MenuResult result) implements SettingsEvent {}
        record Close() implements SettingsEvent {}
    }

    private final PlayerMenu menu;
    private final PlayerProfileSettingsService profileSettings;
    private final Session session;
    private final PlayerData targetData;

    public PlayerSettingsUiController(PlayerMenu menu,
                                      PlayerProfileSettingsService profileSettings,
                                      Session session,
                                      PlayerData targetData) {
        this.menu = menu;
        this.profileSettings = profileSettings;
        this.session = session;
        this.targetData = targetData;
    }

    public static SettingsModel createModel(Session session, PlayerData targetData) {
        return createModel(session, targetData, Tab.PROFILE);
    }

    public static SettingsModel createModel(Session session, PlayerData targetData, Tab initialTab) {
        Objects.requireNonNull(targetData, "targetData");
        String customNick = targetData.customNickname != null ? targetData.customNickname : "";
        String desc = targetData.description != null ? targetData.description : "";
        String playerColorHex = resolvePlayerColorHex(session, targetData);

        Set<String> unlocked = targetData.unlockedBadges != null ? targetData.unlockedBadges : Set.of();
        boolean isSelf = session != null && session.data != null && Objects.equals(session.data.uuid, targetData.uuid);
        boolean admin = targetData.admin || (isSelf && session.player != null && session.player.admin);

        String activeBadge = targetData.activeBadge != null ? targetData.activeBadge : "";
        String initialPreview = !activeBadge.isBlank()
                ? activeBadge
                : (!unlocked.isEmpty() ? unlocked.iterator().next() : Badge.DEVELOPER.id());

        boolean globalChat = targetData.globalChatVisible != null ? targetData.globalChatVisible : true;
        boolean discordRelay = targetData.discordRelayVisible != null ? targetData.discordRelayVisible : false;
        boolean leaderboard = targetData.leaderboard;
        IdentityDisplayMode idMode = targetData.identityDisplayMode != null ? targetData.identityDisplayMode : IdentityDisplayMode.PID;

        return new SettingsModel(
                targetData.uuid,
                targetData.nickname != null ? targetData.nickname : "",
                targetData.username != null ? targetData.username : "",
                targetData.canChangeUsername,
                idMode,
                customNick,
                desc,
                globalChat,
                discordRelay,
                leaderboard,
                activeBadge,
                initialPreview,
                targetData.language != null ? targetData.language : "auto",
                targetData.translatorLanguage != null ? targetData.translatorLanguage : "off",
                targetData.badgeSymbolColorMode != null ? targetData.badgeSymbolColorMode : "default",
                unlocked,
                admin,
                playerColorHex,
                initialTab != null ? initialTab : Tab.PROFILE,
                BadgesFilter.MY,
                "",
                false
        );
    }

    public static String resolvePlayerColorHex(Session session, PlayerData targetData) {
        boolean isSelf = session != null && session.data != null && targetData != null && Objects.equals(session.data.uuid, targetData.uuid);
        if (isSelf && session.player != null && session.player.color != null) {
            String colStr = session.player.color.toString();
            if (colStr != null && colStr.length() >= 6) {
                String hex = colStr.substring(0, 6);
                if (!"ffffff".equalsIgnoreCase(hex) && !"000000".equalsIgnoreCase(hex)) {
                    return hex.toUpperCase(Locale.ROOT);
                }
            }
        }
        String name = targetData != null && targetData.customNickname != null && !targetData.customNickname.isBlank()
                ? targetData.customNickname
                : (targetData != null && targetData.nickname != null ? targetData.nickname : "");
        var m = HEX_COLOR_PATTERN.matcher(name);
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT);
        }
        if (isSelf && session.player != null && session.player.color != null) {
            String colStr = session.player.color.toString();
            if (colStr != null && colStr.length() >= 6) {
                return colStr.substring(0, 6).toUpperCase(Locale.ROOT);
            }
        }
        return "FFD37F";
    }

    public static SettingsModel syncFormValues(SettingsModel current, MenuResult result) {
        if (result == null || result.values == null) return current;
        SettingsModel m = current;
        if (result.values.containsKey("field_nickname")) {
            m = m.withCustomNickname(result.getString("field_nickname", m.customNickname()));
        }
        if (result.values.containsKey("field_description")) {
            m = m.withDescription(result.getString("field_description", m.description()));
        }
        if (result.values.containsKey("check_global_chat")) {
            m = m.withGlobalChatVisible(result.getBool("check_global_chat", m.globalChatVisible()));
        }
        if (result.values.containsKey("check_discord_relay")) {
            m = m.withDiscordRelayVisible(result.getBool("check_discord_relay", m.discordRelayVisible()));
        }
        if (result.values.containsKey("check_leaderboard")) {
            m = m.withLeaderboard(result.getBool("check_leaderboard", m.leaderboard()));
        }
        return m;
    }

    public static String resolveLanguageDisplay(String code) {
        if (code == null || code.isBlank() || "auto".equalsIgnoreCase(code)) {
            return "Auto";
        }
        for (LanguageOption opt : AVAILABLE_LANGUAGES) {
            if (opt.code().equalsIgnoreCase(code)) {
                return opt.displayName();
            }
        }
        return code;
    }

    public static String resolveTranslatorDisplay(String code, Localization local) {
        if (code == null || code.isBlank() || "off".equalsIgnoreCase(code)) {
            return local != null ? local.t("player-settings-translator-off") : "Off";
        }
        return resolveLanguageDisplay(code);
    }

    public static String escapeMarkup(String text) {
        if (text == null || text.isBlank()) return "";
        return text.replace("[", "[[");
    }

    public static String activeBadgeName(Localization local, PlayerData targetData) {
        if (targetData == null) return local != null ? local.t("none") : "";
        Badge badge = Badge.byId(targetData.activeBadge);
        if (badge == null || targetData.unlockedBadges == null || !targetData.unlockedBadges.contains(badge.id())) {
            return local != null ? local.t("none") : "";
        }
        return badgeLabel(local, badge);
    }

    public static String systemBadgeName(Localization local, PlayerData targetData) {
        if (targetData == null) return local != null ? local.t("none") : "";
        return targetData.admin ? badgeLabel(local, Badge.ADMIN) : (local != null ? local.t("none") : "");
    }

    public static String badgeLabel(Localization local, Badge badge) {
        if (badge == null) return "";
        String name = local != null ? local.t(badge.nameKey()) : badge.name();
        return badge.tag() + " [white]" + name + "[]";
    }

    public static String badgeLabelWithColor(Localization local, Badge badge, String symbolColorMode, String playerColorHex) {
        if (badge == null) return "";
        String name = local != null ? local.t(badge.nameKey()) : badge.name();
        String tag = renderBadgeTagExact(badge, symbolColorMode, playerColorHex);
        return tag + " [white]" + name + "[]";
    }

    public static String renderBadgeTagExact(Badge badge, String symbolColorMode, String playerColorHex) {
        if (badge == null) return "";
        if ("player-color".equalsIgnoreCase(symbolColorMode) && playerColorHex != null && !playerColorHex.isBlank()) {
            return badge.tagWithGlyphColor("[#" + playerColorHex + "]");
        }
        return badge.tag();
    }

    public static boolean usesPlayerBadgeSymbolColor(String mode) {
        return "player-color".equalsIgnoreCase(mode);
    }

    public static String buildChatPreviewText(SettingsModel model, Localization local) {
        StringBuilder sb = new StringBuilder();

        if (model.isAdmin()) {
            sb.append(Badge.ADMIN.tag()).append(" ");
        }

        String badgeId = model.previewBadge() != null && !model.previewBadge().isBlank()
                ? model.previewBadge()
                : model.activeBadge();

        if (badgeId != null && !badgeId.isBlank()) {
            Badge b = Badge.byId(badgeId);
            if (b != null) {
                sb.append(renderBadgeTagExact(b, model.badgeSymbolColorMode(), model.playerColorHex())).append(" ");
            }
        }

        String rawName = model.customNickname() != null && !model.customNickname().isBlank()
                ? model.customNickname()
                : (model.nickname() != null && !model.nickname().isBlank() ? model.nickname() : "Player");

        if (rawName.startsWith("[")) {
            sb.append(rawName);
        } else {
            sb.append("[accent]").append(rawName);
        }

        sb.append("[white][lightgray]: [white]Hello world![]");

        return sb.toString();
    }

    @Override
    public SettingsModel initialModel(Object context) {
        Tab tab = context instanceof Tab t ? t : Tab.PROFILE;
        return createModel(session, targetData, tab);
    }

    @Override
    public UpdateResult<SettingsModel> update(SettingsModel model, SettingsEvent event, ControllerContext ctx) {
        return switch (event) {
            case SettingsEvent.Save(MenuResult result) -> {
                SettingsModel updated = syncFormValues(model, result);

                String newNick = updated.customNickname() != null ? updated.customNickname().trim() : "";
                if (!newNick.isEmpty() && profileSettings != null) {
                    var validation = profileSettings.validateCustomNickname(newNick);
                    if (!validation.valid()) {
                        String errKey = validation.errorKey() != null ? validation.errorKey() : "error-nickname-invalid";
                        String localizedErr = session != null
                                ? session.locale().t(errKey, Map.of("max", validation.maxBytes()))
                                : errKey;
                        yield UpdateResult.rerender(updated.withFeedback("[scarlet]⚠ " + localizedErr + "[]", false));
                    }
                }

                if (updated.canChangeUsername() || updated.isAdmin()) {
                    String desiredUsername = result.getString("field_username", "").trim();
                    if (!desiredUsername.isEmpty() && !desiredUsername.equalsIgnoreCase(model.username())) {
                        var usernameVal = profileSettings.validateUsername(desiredUsername);
                        if (!usernameVal.valid()) {
                            String errKey = usernameVal.errorKey();
                            String localizedErr = session != null ? session.locale().t(errKey) : errKey;
                            yield UpdateResult.rerender(updated.withFeedback("[scarlet]⚠ " + localizedErr + "[]", false));
                        }

                        PlayerDataRepository repo = session != null ? session.playerDataRepository : null;
                        if (repo != null) {
                            PlayerData existing = repo.findByUsername(desiredUsername);
                            if (existing != null && !existing.uuid.equals(targetData.uuid)) {
                                String takenErr = session != null ? session.locale().t("error-username-taken") : "Username taken";
                                yield UpdateResult.rerender(updated.withFeedback("[scarlet]⚠ " + takenErr + "[]", false));
                            }
                        }

                        profileSettings.updateUsername(targetData, desiredUsername);
                        updated = updated.withUsername(desiredUsername).withCanChangeUsername(false);
                    }
                }

                updated = updated.withCustomNickname(newNick);

                if (profileSettings != null) {
                    profileSettings.updateCustomNickname(targetData, newNick, true, true);
                    profileSettings.updateDescription(targetData, updated.description() != null ? updated.description().trim() : "");
                    profileSettings.updateGlobalChatVisible(targetData, updated.globalChatVisible());
                    profileSettings.updateDiscordRelayVisible(targetData, updated.discordRelayVisible());
                    profileSettings.updateLeaderboard(targetData, updated.leaderboard());
                }

                String successMsg = session != null
                        ? session.locale().t("player-settings-saved")
                        : "Settings saved!";
                yield UpdateResult.rerender(updated.withFeedback(successMsg, true));
            }

            case SettingsEvent.SelectIdentityDisplayMode(var mode) -> {
                if (profileSettings != null) {
                    profileSettings.updateIdentityDisplayMode(targetData, mode);
                }
                yield UpdateResult.rerender(model.withIdentityDisplayMode(mode).withFeedback("", true));
            }

            case SettingsEvent.ResetNickname(var result) -> {
                SettingsModel updated = syncFormValues(model, result);
                if (profileSettings != null) {
                    profileSettings.updateCustomNickname(targetData, "", true, true);
                }
                String resetMsg = session != null
                        ? session.locale().t("player-settings-reset-feedback")
                        : "Custom nickname reset.";
                yield UpdateResult.rerender(updated.withCustomNickname("").withFeedback(resetMsg, true));
            }

            case SettingsEvent.SelectTab(Tab tab, MenuResult result) -> {
                SettingsModel updated = syncFormValues(model, result);
                yield UpdateResult.rerender(updated.withTab(tab));
            }

            case SettingsEvent.SelectBadgesFilter(BadgesFilter filter) ->
                    UpdateResult.rerender(model.withBadgesFilter(filter));

            case SettingsEvent.ToggleSymbolColorMode() -> {
                String newMode = usesPlayerBadgeSymbolColor(model.badgeSymbolColorMode()) ? "default" : "player-color";
                if (profileSettings != null) {
                    profileSettings.updateBadgeSymbolColorMode(targetData, newMode, true, true);
                }
                yield UpdateResult.rerender(model.withBadgeSymbolColorMode(newMode).withFeedback("", true));
            }

            case SettingsEvent.PreviewBadge(var badgeId) ->
                    UpdateResult.rerender(model.withPreviewBadge(badgeId));

            case SettingsEvent.EquipBadge(var badgeId) -> {
                Badge badge = Badge.byId(badgeId);
                boolean isOwned = model.unlockedBadges() != null && model.unlockedBadges().contains(badgeId);
                if (badge == null || !badge.selectable() || badge.system() || !isOwned) {
                    String err = session != null ? session.locale().t("error-badge-not-unlocked") : "Cannot equip this badge.";
                    yield UpdateResult.rerender(model.withFeedback("[scarlet]⚠ " + err + "[]", false));
                }
                if (profileSettings != null) {
                    profileSettings.updateActiveBadge(targetData, badge.id(), true, true);
                }
                yield UpdateResult.rerender(model.withActiveBadge(badge.id()).withPreviewBadge(badge.id()).withFeedback("", true));
            }

            case SettingsEvent.UnequipBadge() -> {
                if (profileSettings != null) {
                    profileSettings.updateActiveBadge(targetData, "", true, true);
                }
                yield UpdateResult.rerender(model.withActiveBadge("").withFeedback("", true));
            }

            case SettingsEvent.SelectLanguage(var code, var result) -> {
                SettingsModel updated = syncFormValues(model, result);
                if (profileSettings != null) {
                    profileSettings.updateLanguage(targetData, code);
                }
                yield UpdateResult.rerender(updated.withLanguage(code).withFeedback("", true));
            }

            case SettingsEvent.SelectTranslatorLanguage(var code, var result) -> {
                SettingsModel updated = syncFormValues(model, result);
                if (profileSettings != null) {
                    profileSettings.updateTranslatorLanguage(targetData, code);
                }
                yield UpdateResult.rerender(updated.withTranslatorLanguage(code).withFeedback("", true));
            }

            case SettingsEvent.Close() -> UpdateResult.close(model);
        };
    }

    @Override
    public VNode render(SettingsModel model) {
        DialogMetrics metrics = metrics();
        Localization local = session != null ? session.locale() : null;

        return Ui.table(t -> {
            t.background("pane");
            t.margin(12f);
            t.layout(l -> l.growX().maxWidth(metrics.maxDialogWidth()).pad(4f));

            t.add(Ui.table(h -> {
                h.layout(l -> l.growX().padBottom(4f));
                h.label(Text.raw("[orange]" + Iconc.admin + "[]  [lightgray]|[]  [white]"), l -> l.align("left"));
                h.label(Text.t("player-menu-settings-title"), l -> l.align("left").growX());
                h.button(Text.raw(" [scarlet]✕[] "), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.size(34f)));
            })).row();
            t.image("whiteui", l -> l.growX().height(3f).padBottom(6f).color("ffd37f")).row();

            t.add(Ui.table(tabs -> {
                tabs.layout(l -> l.growX().padBottom(6f));
                float tabHeight = 36f;

                tabs.button(Text.raw(Iconc.admin + " " + (local != null ? local.t("player-settings-tab-profile") : "Profile")),
                        "action:tab:profile", b -> b
                                .style("togglet")
                                .checked(model.tab() == Tab.PROFILE)
                                .layout(l -> l.uniform().growX().height(tabHeight).padRight(4f)));

                tabs.button(Text.raw(Iconc.chat + " " + (local != null ? local.t("player-settings-tab-chat") : "Chat & Lang")),
                        "action:tab:chat_lang", b -> b
                                .style("togglet")
                                .checked(model.tab() == Tab.CHAT_LANG)
                                .layout(l -> l.uniform().growX().height(tabHeight).padRight(4f)));

                String badgesCountStr = model.unlockedBadges() != null && !model.unlockedBadges().isEmpty()
                        ? " (" + model.unlockedBadges().size() + ")" : "";
                tabs.button(Text.raw(Iconc.star + " " + (local != null ? local.t("player-settings-tab-badges") : "Badges") + badgesCountStr),
                        "action:tab:badges", b -> b
                                .style("togglet")
                                .checked(model.tab() == Tab.BADGES)
                                .layout(l -> l.uniform().growX().height(tabHeight)));
            })).row();
            t.image("whiteui", l -> l.growX().height(2f).padBottom(8f).color("454545")).row();

            t.pane(p -> {
                p.layout(l -> l.growX().growY().maxHeight(metrics.maxBodyHeight()));
                p.table(body -> {
                    body.layout(l -> l.growX().fillX());

                    switch (model.tab()) {
                        case PROFILE -> renderProfileTab(body, model, metrics, local);
                        case CHAT_LANG -> renderChatLangTab(body, model, metrics, local);
                        case BADGES -> renderBadgesTab(body, model, metrics, local);
                    }
                });
            }).row();

            if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
                t.image("whiteui", l -> l.growX().height(2f).padTop(4f).padBottom(4f).color("454545")).row();
                t.add(Ui.table(fb -> {
                    fb.layout(l -> l.growX().padTop(2f).padBottom(2f));
                    fb.label(Text.raw(model.feedbackMessage()), l -> l.align("center").growX());
                })).row();
            }

            t.add(Ui.table(actions -> {
                actions.layout(l -> l.growX().padTop(6f));
                actions.button(Text.t("cancel"), "action:close", b -> b
                        .style("cleart")
                        .layout(l -> l.growX().uniform().height(44f).padRight(4f)));
                actions.button(Text.join(Text.raw("[accent]"), Text.t("save")), "action:save", b -> b
                        .style("cleart")
                        .layout(l -> l.growX().uniform().height(44f)));
            })).row();
        });
    }

    private DialogMetrics metrics() {
        return DialogMetrics.standard();
    }

    private void renderProfileTab(Ui.TableBuilder body, SettingsModel model, DialogMetrics metrics, Localization local) {
        body.add(Ui.table(row -> {
            row.layout(l -> l.growX().padBottom(4f));
            String nameFormatted = model.nickname().startsWith("[") ? model.nickname() : "[white]" + model.nickname();
            row.label(Text.join(Text.t("player-settings-player-label"), Text.raw("  " + nameFormatted + "[]")),
                    l -> l.align("left").growX());
            row.button(Text.t("player-settings-reset-nick-btn"), "action:reset_nick", b -> b
                    .style("cleart")
                    .layout(l -> l.height(32f).padLeft(8f)));
        })).row();

        // Секція імені користувача (Редагування або Заблоковано)
        boolean hasUsername = model.username() != null && !model.username().isBlank();
        boolean canEdit = model.canChangeUsername() || model.isAdmin();

        if (canEdit) {
            body.label(Text.t("player-settings-username-editable"), l -> l.align("left").padBottom(2f)).row();
            body.field("field_username", f -> f
                    .value(model.username())
                    .hint(local != null ? local.t("player-settings-username-hint") : "")
                    .maxLength(32)
                    .layout(l -> l.growX().height(38f).padBottom(8f))).row();
        } else {
            body.add(Ui.table(userBox -> {
                userBox.layout(l -> l.growX().padBottom(8f));
                if (hasUsername) {
                    String lockedText = local != null
                            ? local.t("player-settings-username-locked", args("username", model.username()))
                            : "@" + model.username();
                    userBox.label(Text.raw(lockedText), l -> l.align("left").growX());
                } else {
                    userBox.label(Text.t("player-settings-username-none"), l -> l.align("left").growX());
                }
            })).row();
        }

        // =========================================================================
        // СЕКЦІЯ: Налаштування показу ідентифікатора у сповіщеннях (4 варіанти)
        // =========================================================================
        body.image("whiteui", l -> l.growX().height(2f).padBottom(8f).color("454545")).row();
        body.label(Text.t("player-settings-identity-mode"), l -> l.align("left").padBottom(4f)).row();

        body.add(Ui.table(modes -> {
            modes.wrap();
            modes.layout(l -> l.growX().padBottom(10f));

            IdentityDisplayMode cur = model.identityDisplayMode() != null ? model.identityDisplayMode() : IdentityDisplayMode.PID;

            for (IdentityDisplayMode mode : IdentityDisplayMode.values()) {
                boolean active = cur == mode;
                String bundleKey = switch (mode) {
                    case PID -> "player-settings-identity-mode-pid";
                    case USERNAME -> "player-settings-identity-mode-username";
                    case BOTH -> "player-settings-identity-mode-both";
                    case NONE -> "player-settings-identity-mode-none";
                };

                modes.button(Text.t(bundleKey), "action:identity_mode:" + mode.name().toLowerCase(Locale.ROOT), b -> b
                        .style("togglet")
                        .checked(active)
                        .layout(l -> l.uniform().growX().height(34f).pad(2f)));
            }
        })).row();

        body.image("whiteui", l -> l.growX().height(2f).padBottom(10f).color("454545")).row();

        // Власний нікнейм
        String cleanHint = Strings.stripColors(local != null ? local.t("player-menu-settings-customNickname-message") : "");
        body.label(Text.t("player-menu-settings-customNickname"), l -> l.align("left").padBottom(2f)).row();
        body.field("field_nickname", f -> f
                .value(model.customNickname())
                .hint(cleanHint)
                .maxLength(256)
                .layout(l -> l.growX().height(38f).padBottom(8f))).row();

        // Опис профілю
        body.label(Text.t("player-menu-settings-description"), l -> l.align("left").padBottom(2f)).row();
        body.field("field_description", f -> f
                .value(model.description())
                .maxLength(200)
                .layout(l -> l.growX().height(38f).padBottom(10f))).row();

        body.image("whiteui", l -> l.growX().height(2f).padBottom(10f).color("454545")).row();

        // Таблиця лідерів
        body.check(Text.t("player-settings-leaderboard"), c -> c
                .id("check_leaderboard")
                .checked(model.leaderboard())
                .layout(l -> l.align("left").padBottom(10f))).row();

        body.image("whiteui", l -> l.growX().height(2f).padBottom(10f).color("454545")).row();

        // Відзнаки
        body.add(Ui.table(badgeBox -> {
            badgeBox.layout(l -> l.growX().pad(4f));
            String activeBadgeStr;
            if (model.activeBadge() != null && !model.activeBadge().isBlank()) {
                Badge b = Badge.byId(model.activeBadge());
                activeBadgeStr = b != null ? badgeLabelWithColor(local, b, model.badgeSymbolColorMode(), model.playerColorHex()) : model.activeBadge();
            } else {
                activeBadgeStr = "[gray]" + (local != null ? local.t("none") : "None") + "[]";
            }
            badgeBox.label(Text.raw("[accent]" + Iconc.star + " " + (local != null ? local.t("player-settings-tab-badges") : "Badge") + ":[] " + activeBadgeStr),
                    l -> l.align("left").growX());
            badgeBox.button(Text.raw("[accent]" + (local != null ? local.t("player-settings-manage-badges") : "Manage Badges →") + "[]"),
                    "action:tab:badges", b -> b
                            .style("cleart")
                            .layout(l -> l.height(34f).padLeft(8f)));
        })).row();
    }

    private void renderChatLangTab(Ui.TableBuilder body, SettingsModel model, DialogMetrics metrics, Localization local) {
        body.label(Text.raw("[accent]" + Iconc.chat + " " + (local != null ? local.t("player-menu-settings-chat") : "Chat Visibility") + "[]"),
                l -> l.align("left").padBottom(4f)).row();

        body.check(Text.t("player-settings-global-chat"), c -> c
                .id("check_global_chat")
                .checked(model.globalChatVisible())
                .layout(l -> l.align("left").padBottom(6f))).row();

        body.check(Text.t("player-settings-discord-relay"), c -> c
                .id("check_discord_relay")
                .checked(model.discordRelayVisible())
                .layout(l -> l.align("left").padBottom(10f))).row();

        body.image("whiteui", l -> l.growX().height(2f).padBottom(10f).color("454545")).row();

        String curLang = resolveLanguageDisplay(model.language());
        body.label(Text.raw("[accent]" + Iconc.bookOpen + " " + (local != null ? local.t("player-settings-language") : "Interface Language")
                + ":[]  [lime]" + curLang + "[]"), l -> l.align("left").padBottom(6f)).row();

        body.add(Ui.table(grid -> {
            grid.wrap();
            grid.layout(l -> l.growX().padBottom(10f));
            for (LanguageOption opt : AVAILABLE_LANGUAGES) {
                boolean isSel = opt.code().equalsIgnoreCase(model.language());
                grid.button(Text.raw((isSel ? "[accent]● " : "") + opt.displayName() + "[]"),
                        "action:select_lang:" + opt.code(), b -> b
                                .style("togglet")
                                .checked(isSel)
                                .layout(l -> l.uniform().growX().height(36f).pad(2f)));
            }
        })).row();

        body.image("whiteui", l -> l.growX().height(2f).padBottom(10f).color("454545")).row();

        String curTrans = resolveTranslatorDisplay(model.translatorLanguage(), local);
        body.label(Text.raw("[accent]" + Iconc.chat + " " + (local != null ? local.t("player-settings-translator-lang") : "Chat Translator")
                + ":[]  [lime]" + curTrans + "[]"), l -> l.align("left").padBottom(6f)).row();

        body.add(Ui.table(grid -> {
            grid.wrap();
            grid.layout(l -> l.growX().padBottom(6f));

            boolean isOff = model.translatorLanguage() == null || model.translatorLanguage().isBlank() || "off".equalsIgnoreCase(model.translatorLanguage());
            grid.button(Text.raw((isOff ? "[accent]● " : "") + (local != null ? local.t("player-settings-translator-off") : "Off") + "[]"),
                    "action:select_translator:off", b -> b
                            .style("togglet")
                            .checked(isOff)
                            .layout(l -> l.uniform().growX().height(36f).pad(2f)));

            for (LanguageOption opt : AVAILABLE_LANGUAGES) {
                if ("auto".equalsIgnoreCase(opt.code())) continue;
                boolean isSel = opt.code().equalsIgnoreCase(model.translatorLanguage());
                grid.button(Text.raw((isSel ? "[accent]● " : "") + opt.displayName() + "[]"),
                        "action:select_translator:" + opt.code(), b -> b
                                .style("togglet")
                                .checked(isSel)
                                .layout(l -> l.uniform().growX().height(36f).pad(2f)));
            }
        })).row();
    }

    private void renderBadgesTab(Ui.TableBuilder body, SettingsModel model, DialogMetrics metrics, Localization local) {
        body.add(Ui.table(previewBox -> {
            previewBox.background("button");
            previewBox.margin(8f);
            previewBox.layout(l -> l.growX().padBottom(8f));

            Badge toPreview = model.previewBadge() != null && !model.previewBadge().isBlank()
                    ? Badge.byId(model.previewBadge())
                    : (model.activeBadge() != null && !model.activeBadge().isBlank() ? Badge.byId(model.activeBadge()) : null);

            String badgeNameInfo = "";
            if (toPreview != null) {
                String badgeTitle = local != null ? local.t(toPreview.nameKey()) : toPreview.name();
                if (model.activeBadge() == null || !model.activeBadge().equals(toPreview.id())) {
                    String samplePrefix = local != null ? local.t("player-settings-chat-preview-sample") : "Sample";
                    badgeNameInfo = " [lightgray](" + samplePrefix + ": [accent]" + badgeTitle + "[lightgray])[]";
                } else {
                    badgeNameInfo = " [lightgray]([lime]" + badgeTitle + "[lightgray])[]";
                }
            }

            previewBox.label(Text.raw("[accent]" + Iconc.chat + " " + (local != null ? local.t("player-settings-chat-preview") : "Chat Preview")
                    + ":[]" + badgeNameInfo), l -> l.align("left").growX()).row();

            previewBox.label(Text.raw(buildChatPreviewText(model, local)),
                    l -> l.align("left").growX());
        })).row();

        body.add(Ui.table(colorRow -> {
            colorRow.layout(l -> l.growX().padBottom(8f));
            colorRow.label(Text.raw("[accent]" + (local != null ? local.t("player-settings-symbol-color-mode") : "Symbol Color:") + "[]"),
                    l -> l.align("left").padRight(6f));

            boolean usesPlayerColor = usesPlayerBadgeSymbolColor(model.badgeSymbolColorMode());
            colorRow.button(Text.raw("[white]● []" + (local != null ? local.t("badge-menu-symbol-color-default") : "Default")),
                    "action:toggle_symbol_color", b -> b
                            .style("togglet")
                            .checked(!usesPlayerColor)
                            .layout(l -> l.uniform().growX().height(32f).padRight(4f)));

            String playerColorTag = "[#" + model.playerColorHex() + "]● []";
            colorRow.button(Text.raw(playerColorTag + (local != null ? local.t("badge-menu-symbol-color-player-color") : "Player Color")),
                    "action:toggle_symbol_color", b -> b
                            .style("togglet")
                            .checked(usesPlayerColor)
                            .layout(l -> l.uniform().growX().height(32f)));
        })).row();

        List<Badge> myBadges = new ArrayList<>();
        for (Badge b : Badge.selectableManualBadges()) {
            if (model.unlockedBadges() != null && model.unlockedBadges().contains(b.id())) {
                myBadges.add(b);
            }
        }

        body.add(Ui.table(filters -> {
            filters.layout(l -> l.growX().padBottom(8f));
            String myLabel = (local != null ? local.t("player-settings-badges-my") : "My Badges") + " (" + myBadges.size() + ")";
            String allLabel = (local != null ? local.t("player-settings-badges-all") : "All Badges") + " (" + Badge.values().length + ")";

            filters.button(Text.raw(Iconc.star + " " + myLabel), "action:badges_filter:my", b -> b
                    .style("togglet")
                    .checked(model.badgesFilter() == BadgesFilter.MY)
                    .layout(l -> l.uniform().growX().height(34f).padRight(4f)));

            filters.button(Text.raw(Iconc.zoom + " " + allLabel), "action:badges_filter:all", b -> b
                    .style("togglet")
                    .checked(model.badgesFilter() == BadgesFilter.ALL)
                    .layout(l -> l.uniform().growX().height(34f)));
        })).row();

        if (model.badgesFilter() == BadgesFilter.MY) {
            if (myBadges.isEmpty()) {
                body.add(Ui.table(emptyBox -> {
                    emptyBox.layout(l -> l.growX().pad(16f));
                    emptyBox.label(Text.raw("[gray]" + Iconc.warning + " " + (local != null ? local.t("player-settings-badges-empty") : "No badges unlocked yet.") + "[]"),
                            l -> l.align("center").growX()).row();
                    emptyBox.button(Text.raw("[accent]" + (local != null ? local.t("player-settings-badges-all") : "Browse All Badges") + "[]"),
                            "action:badges_filter:all", b -> b
                                    .style("cleart")
                                    .layout(l -> l.height(36f).padTop(8f)));
                })).row();
            } else {
                for (Badge badge : myBadges) {
                    renderBadgeCard(body, badge, model, metrics, local);
                }
            }
        } else {
            for (Badge badge : Badge.values()) {
                renderBadgeCard(body, badge, model, metrics, local);
            }
        }
    }

    private void renderBadgeCard(Ui.TableBuilder body, Badge badge, SettingsModel model, DialogMetrics metrics, Localization local) {
        boolean isEquipped = badge.id().equals(model.activeBadge());
        boolean isUnlocked = (model.unlockedBadges() != null && model.unlockedBadges().contains(badge.id())) || badge.system();
        boolean isSystem = badge.system();

        body.add(Ui.table(card -> {
            card.background("button");
            card.margin(8f);
            card.layout(l -> l.growX().padBottom(6f));

            card.add(Ui.table(top -> {
                top.layout(l -> l.growX());

                String label = badgeLabelWithColor(local, badge, model.badgeSymbolColorMode(), model.playerColorHex());
                top.label(Text.raw(label), l -> l.align("left").growX());

                String statusStr;
                if (isSystem) {
                    statusStr = model.isAdmin() ? "[coral]● " + (local != null ? local.t("badge-state-system-active") : "System Active") + "[]"
                            : "[gray]🔒 " + (local != null ? local.t("badge-state-system") : "System") + "[]";
                } else if (isEquipped) {
                    statusStr = "[lime]● " + (local != null ? local.t("badge-state-active") : "Equipped") + "[]";
                } else if (isUnlocked) {
                    statusStr = "[accent]✓ " + (local != null ? local.t("badge-state-unlocked") : "Unlocked") + "[]";
                } else {
                    statusStr = "[darkgray]🔒 " + (local != null ? local.t("badge-state-locked") : "Locked") + "[]";
                }
                top.label(Text.raw(statusStr), l -> l.align("right").padRight(6f));

                boolean isPreviewing = badge.id().equals(model.previewBadge());
                if (isPreviewing) {
                    top.label(Text.raw("[sky]● " + (local != null ? local.t("player-settings-badge-previewing") : "Previewing") + "[]"),
                            l -> l.align("right").padRight(6f));
                } else {
                    top.button(Text.raw("[sky]" + (local != null ? local.t("player-settings-badge-preview") : "Preview") + "[]"),
                            "action:preview_badge:" + badge.id(), b -> b
                                    .style("cleart")
                                    .layout(l -> l.height(30f).padRight(4f)));
                }

                if (isEquipped) {
                    top.button(Text.raw("[scarlet]" + (local != null ? local.t("player-settings-badge-unequip") : "Unequip") + "[]"),
                            "action:unequip_badge", b -> b
                                    .style("cleart")
                                    .layout(l -> l.height(30f)));
                } else if (isUnlocked && badge.selectable() && !badge.system()) {
                    top.button(Text.raw("[accent]" + (local != null ? local.t("player-settings-badge-equip") : "Equip") + "[]"),
                            "action:equip_badge:" + badge.id(), b -> b
                                    .style("cleart")
                                    .layout(l -> l.height(30f)));
                }
            })).row();

            String desc = local != null ? local.t(badge.descriptionKey()) : "";
            if (!desc.isBlank()) {
                card.add(Ui.table(bot -> {
                    bot.layout(l -> l.growX().padTop(2f));
                    bot.label(Text.raw("[lightgray]" + desc + "[]"), l -> l.align("left").growX());
                })).row();
            }
        })).row();
    }

    @Override
    public SettingsEvent parseEvent(MenuResult result) {
        if (result == null) return null;
        if (result.wasCancelled()) return new SettingsEvent.Close();

        String res = result.result;
        if (res == null) return null;

        if ("action:close".equals(res)) return new SettingsEvent.Close();
        if ("action:save".equals(res)) return new SettingsEvent.Save(result);
        if ("action:reset_nick".equals(res)) return new SettingsEvent.ResetNickname(result);
        if ("action:toggle_symbol_color".equals(res)) return new SettingsEvent.ToggleSymbolColorMode();
        if ("action:unequip_badge".equals(res)) return new SettingsEvent.UnequipBadge();

        if (res.startsWith("action:identity_mode:")) {
            String modeStr = res.substring("action:identity_mode:".length()).toUpperCase(Locale.ROOT);
            try {
                IdentityDisplayMode mode = IdentityDisplayMode.valueOf(modeStr);
                return new SettingsEvent.SelectIdentityDisplayMode(mode);
            } catch (IllegalArgumentException ignored) {}
        }

        if (res.startsWith("action:tab:")) {
            String tabStr = res.substring("action:tab:".length()).toUpperCase(Locale.ROOT);
            try {
                Tab tab = Tab.valueOf(tabStr);
                return new SettingsEvent.SelectTab(tab, result);
            } catch (IllegalArgumentException ignored) {}
        }

        if (res.startsWith("action:badges_filter:")) {
            String fStr = res.substring("action:badges_filter:".length()).toUpperCase(Locale.ROOT);
            try {
                BadgesFilter f = BadgesFilter.valueOf(fStr);
                return new SettingsEvent.SelectBadgesFilter(f);
            } catch (IllegalArgumentException ignored) {}
        }

        if (res.startsWith("action:preview_badge:")) {
            return new SettingsEvent.PreviewBadge(res.substring("action:preview_badge:".length()));
        }

        if (res.startsWith("action:equip_badge:")) {
            return new SettingsEvent.EquipBadge(res.substring("action:equip_badge:".length()));
        }

        if (res.startsWith("action:select_lang:")) {
            return new SettingsEvent.SelectLanguage(res.substring("action:select_lang:".length()), result);
        }

        if (res.startsWith("action:select_translator:")) {
            return new SettingsEvent.SelectTranslatorLanguage(res.substring("action:select_translator:".length()), result);
        }

        return null;
    }
}
