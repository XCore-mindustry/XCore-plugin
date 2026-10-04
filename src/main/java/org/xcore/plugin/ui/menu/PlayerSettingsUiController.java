package org.xcore.plugin.ui.menu;

import arc.util.Strings;
import mindustry.gen.Iconc;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.kit.Accent;
import org.xcore.plugin.ui.kit.Kit;
import org.xcore.plugin.ui.kit.Kit.Option;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.xcore.plugin.ui.kit.Kit.BUTTON_HEIGHT;
import static org.xcore.plugin.ui.kit.Kit.FIELD_HEIGHT;
import static org.xcore.plugin.ui.kit.Kit.GAP;
import static org.xcore.plugin.ui.kit.Kit.MARGIN;
import static org.xcore.plugin.ui.kit.Kit.PAD;
import static org.xcore.plugin.ui.kit.Kit.TAB_GAP;

/**
 * The player settings: profile, chat, language and badges, on four tabs of cards, laid out once
 * per {@link Screen}.
 */
public class PlayerSettingsUiController implements UiController<PlayerSettingsUiController.SettingsModel, PlayerSettingsUiController.SettingsEvent> {

    private static final float STATE_ICON = 24f;
    /** The narrowest a button holds the longest language name, a colour mode, a badge filter. */
    private static final float LANGUAGE_OPTION = 124f;
    private static final float COLOR_OPTION = 290f;
    private static final float FILTER_OPTION = 150f;

    /** A tab of the settings; each has its own colour so the cards of a topic read as one group. */
    public enum Tab {
        PROFILE(Accent.GOLD, Iconc.players),
        CHAT(Accent.BLUE, Iconc.chat),
        LANGUAGE(Accent.GREEN, Iconc.planet),
        BADGES(Accent.PURPLE, Iconc.star);

        private final Accent accent;
        private final char glyph;

        Tab(Accent accent, char glyph) {
            this.accent = accent;
            this.glyph = glyph;
        }

        String key() {
            return "player-settings-tab-" + name().toLowerCase(Locale.ROOT);
        }

        String action() {
            return "action:tab:" + name().toLowerCase(Locale.ROOT);
        }
    }

    /** A setting that is either on or off and changes the moment its switch is pressed. */
    public enum Setting {
        GLOBAL_CHAT,
        DISCORD_RELAY,
        LEADERBOARD;

        String action() {
            return "action:toggle:" + name().toLowerCase(Locale.ROOT);
        }
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
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, newTab, badgesFilter,
                    "", true);
        }

        public SettingsModel withBadgesFilter(BadgesFilter filter) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, filter,
                    "", true);
        }

        public SettingsModel withCustomNickname(String nick) {
            return new SettingsModel(targetUuid, nickname, nick, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withDescription(String desc) {
            return new SettingsModel(targetUuid, nickname, customNickname, desc, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withGlobalChatVisible(boolean visible) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, visible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withDiscordRelayVisible(boolean visible) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
                    visible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withLeaderboard(boolean lb) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
                    discordRelayVisible, lb, activeBadge, previewBadge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withActiveBadge(String badge) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, badge, badge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withPreviewBadge(String badge) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, badge, language, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withLanguage(String lang) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, lang, translatorLanguage,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withTranslatorLanguage(String lang) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, lang,
                    badgeSymbolColorMode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withBadgeSymbolColorMode(String mode) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
                    discordRelayVisible, leaderboard, activeBadge, previewBadge, language, translatorLanguage,
                    mode, unlockedBadges, isAdmin, playerColorHex, tab, badgesFilter,
                    feedbackMessage, isSuccess);
        }

        public SettingsModel withFeedback(String msg, boolean success) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible,
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
        record Toggle(Setting setting, MenuResult result) implements SettingsEvent {}
        record SelectSymbolColorMode(String mode) implements SettingsEvent {}
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

        return new SettingsModel(
                targetData.uuid,
                targetData.nickname != null ? targetData.nickname : "",
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

        // 1. Admin badge (if admin)
        if (model.isAdmin()) {
            sb.append(Badge.ADMIN.tag()).append(" ");
        }

        // 2. Active or previewed badge
        String badgeId = model.previewBadge() != null && !model.previewBadge().isBlank()
                ? model.previewBadge()
                : model.activeBadge();

        if (badgeId != null && !badgeId.isBlank()) {
            Badge b = Badge.byId(badgeId);
            if (b != null) {
                sb.append(renderBadgeTagExact(b, model.badgeSymbolColorMode(), model.playerColorHex())).append(" ");
            }
        }

        // 3. Player name (preserving existing color tags, or prepending [accent] if plain)
        String rawName = model.customNickname() != null && !model.customNickname().isBlank()
                ? model.customNickname()
                : (model.nickname() != null && !model.nickname().isBlank() ? model.nickname() : "Player");

        if (rawName.startsWith("[")) {
            sb.append(rawName);
        } else {
            sb.append("[accent]").append(rawName);
        }

        // 4. Message suffix
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

                updated = updated.withCustomNickname(newNick);

                if (profileSettings != null) {
                    profileSettings.updateCustomNickname(targetData, newNick, true, true);
                    profileSettings.updateDescription(targetData, updated.description() != null ? updated.description().trim() : "");
                }

                String successMsg = session != null
                        ? session.locale().t("player-settings-saved")
                        : "Settings saved!";
                yield UpdateResult.rerender(updated.withFeedback(successMsg, true));
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

            case SettingsEvent.Toggle(Setting setting, MenuResult result) -> {
                // What is typed on the tab but not saved yet must survive the page being built again.
                SettingsModel updated = syncFormValues(model, result);
                updated = switch (setting) {
                    case GLOBAL_CHAT -> {
                        boolean visible = !updated.globalChatVisible();
                        if (profileSettings != null) profileSettings.updateGlobalChatVisible(targetData, visible);
                        yield updated.withGlobalChatVisible(visible);
                    }
                    case DISCORD_RELAY -> {
                        boolean visible = !updated.discordRelayVisible();
                        if (profileSettings != null) profileSettings.updateDiscordRelayVisible(targetData, visible);
                        yield updated.withDiscordRelayVisible(visible);
                    }
                    case LEADERBOARD -> {
                        boolean shown = !updated.leaderboard();
                        if (profileSettings != null) profileSettings.updateLeaderboard(targetData, shown);
                        yield updated.withLeaderboard(shown);
                    }
                };
                yield UpdateResult.rerender(updated.withFeedback("", true));
            }

            case SettingsEvent.SelectSymbolColorMode(String mode) -> {
                String current = usesPlayerBadgeSymbolColor(model.badgeSymbolColorMode()) ? "player-color" : "default";
                if (current.equals(mode)) {
                    yield UpdateResult.of(model);
                }
                if (profileSettings != null) {
                    profileSettings.updateBadgeSymbolColorMode(targetData, mode, true, true);
                }
                yield UpdateResult.rerender(model.withBadgeSymbolColorMode(mode).withFeedback("", true));
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

            case SettingsEvent.Close() -> {
                yield UpdateResult.close(model);
            }
        };
    }

    @Override
    public VNode render(SettingsModel model) {
        return Screen.each(screen -> window(model, screen));
    }

    /** The settings laid out for one class of screens. */
    VNode window(SettingsModel model, Screen screen) {
        Tab current = model.tab();
        float width = screen.width();

        return Kit.window(window -> {
            window.add(Kit.header(width, "[accent]" + Iconc.settings + "[] " + t("player-menu-settings-title")
                    + "[]  " + playerName(model))).row();
            window.add(tabs(width, model)).row();
            window.add(Kit.line(width, current.accent)).row();

            if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
                window.add(Kit.feedback(width, model.feedbackMessage(), model.isSuccess())).row();
            }

            Kit.body(window, screen, body -> {
                switch (current) {
                    case PROFILE -> profile(body, screen, model);
                    case CHAT -> chat(body, screen, model);
                    case LANGUAGE -> language(body, screen, model);
                    case BADGES -> badges(body, screen, model);
                }
            });
        });
    }

    private VNode tabs(float width, SettingsModel model) {
        List<Kit.Tab> tabs = new ArrayList<>();
        for (Tab tab : Tab.values()) {
            String count = tab == Tab.BADGES && !model.unlockedBadges().isEmpty()
                    ? " (" + model.unlockedBadges().size() + ")" : "";
            tabs.add(new Kit.Tab(tab.glyph, t(tab.key()) + count, tab.action(), tab.accent, tab == model.tab()));
        }
        return Kit.tabs(width, "tabs", tabs);
    }

    // ------------------------------------------------------------------ tabs

    private void profile(Ui.TableBuilder body, Screen screen, SettingsModel model) {
        VNode identity = Kit.card(screen.card(), Tab.PROFILE.accent, Iconc.pencil + " " + t("player-settings-identity"),
                (content, inner) -> {
                    float half = (inner - TAB_GAP) / 2f;
                    content.labelWrap(Text.raw("[lightgray]" + t("player-menu-settings-customNickname") + "[]"),
                            l -> l.width(inner).padBottom(2f)).row();
                    content.field("field_nickname", f -> f
                            .value(model.customNickname())
                            .hint(Strings.stripColors(t("player-menu-settings-customNickname-message")))
                            .maxLength(256)
                            .layout(l -> l.width(inner).height(FIELD_HEIGHT).padBottom(GAP))).row();
                    content.labelWrap(Text.raw("[lightgray]" + t("player-menu-settings-description") + "[]"),
                            l -> l.width(inner).padBottom(2f)).row();
                    content.field("field_description", f -> f
                            .value(model.description())
                            .maxLength(200)
                            .layout(l -> l.width(inner).height(FIELD_HEIGHT).padBottom(GAP))).row();
                    content.add(Ui.table(actions -> {
                        actions.button(Text.raw(t("player-menu-settings-customNickname-reset")),
                                "action:reset_nick", b -> b
                                        .style("flatBordert")
                                        .layout(l -> l.width(half).height(BUTTON_HEIGHT).padRight(TAB_GAP)));
                        actions.button(Text.raw("[accent]" + Iconc.save + " " + t("save") + "[]"),
                                "action:save", b -> b
                                        .style("flatBordert")
                                        .layout(l -> l.width(half).height(BUTTON_HEIGHT)));
                    })).row();
                });

        VNode display = Kit.card(screen.card(), Tab.PROFILE.accent, Iconc.chartBar + " " + t("player-settings-interface"),
                (content, inner) -> content.add(toggle(Setting.LEADERBOARD, model.leaderboard(),
                        "player-settings-leaderboard", inner, 0f)).row());

        VNode badge = Kit.card(screen.card(), Tab.PROFILE.accent, Iconc.star + " " + t("player-settings-tab-badges"),
                (content, inner) -> {
                    Badge active = Badge.byId(model.activeBadge());
                    String name = active != null
                            ? badgeLabelWithColor(local(), active, model.badgeSymbolColorMode(), model.playerColorHex())
                            : "[gray]" + t("none") + "[]";
                    content.labelWrap(Text.raw(name), l -> l.width(inner).padBottom(GAP)).row();
                    content.button(Text.raw("[accent]" + t("player-settings-manage-badges") + " " + Iconc.right + "[]"),
                            Tab.BADGES.action(), b -> b
                                    .style("flatBordert")
                                    .layout(l -> l.width(inner).height(BUTTON_HEIGHT))).row();
                });

        body.add(Kit.columns(screen, List.of(identity), List.of(display, badge))).row();
    }

    private void chat(Ui.TableBuilder body, Screen screen, SettingsModel model) {
        VNode visibility = Kit.card(screen.card(), Tab.CHAT.accent, Iconc.chat + " " + t("player-menu-settings-chat"),
                (content, inner) -> {
                    content.add(toggle(Setting.GLOBAL_CHAT, model.globalChatVisible(),
                            "player-settings-global-chat", inner, TAB_GAP)).row();
                    content.add(toggle(Setting.DISCORD_RELAY, model.discordRelayVisible(),
                            "player-settings-discord-relay", inner, 0f)).row();
                });

        boolean off = isTranslatorOff(model.translatorLanguage());
        List<Option> options = new ArrayList<>();
        options.add(new Option(t("player-settings-translator-off"), "action:select_translator:off", off));
        for (LanguageOption language : AVAILABLE_LANGUAGES) {
            if ("auto".equalsIgnoreCase(language.code())) continue;
            options.add(new Option(language.displayName(), "action:select_translator:" + language.code(),
                    !off && language.code().equalsIgnoreCase(model.translatorLanguage())));
        }
        VNode translator = Kit.card(screen.card(), Tab.CHAT.accent,
                Iconc.bookOpen + " " + t("player-settings-translator-lang") + ": [white]"
                        + resolveTranslatorDisplay(model.translatorLanguage(), local()),
                (content, inner) -> {
                    content.labelWrap(Text.raw("[lightgray]" + t("player-settings-translator-hint") + "[]"),
                            l -> l.width(inner).padBottom(GAP)).row();
                    Kit.options(content, inner, LANGUAGE_OPTION, "translator", options);
                });

        body.add(Kit.columns(screen, List.of(visibility), List.of(translator))).row();
    }

    private void language(Ui.TableBuilder body, Screen screen, SettingsModel model) {
        List<Option> options = new ArrayList<>();
        for (LanguageOption language : AVAILABLE_LANGUAGES) {
            options.add(new Option(language.displayName(), "action:select_lang:" + language.code(),
                    language.code().equalsIgnoreCase(model.language())));
        }
        body.add(Kit.card(screen.cards(), Tab.LANGUAGE.accent,
                Iconc.planet + " " + t("player-settings-language") + ": [white]" + resolveLanguageDisplay(model.language()),
                (content, inner) -> {
                    content.labelWrap(Text.raw("[lightgray]" + t("player-settings-language-hint") + "[]"),
                            l -> l.width(inner).padBottom(GAP)).row();
                    Kit.options(content, inner, LANGUAGE_OPTION, "language", options);
                })).row();
    }

    private void badges(Ui.TableBuilder body, Screen screen, SettingsModel model) {
        float full = screen.cards();

        body.add(Kit.card(full, Tab.BADGES.accent, Iconc.eye + " " + t("player-settings-chat-preview"), (content, inner) -> {
            content.add(Ui.table(line -> {
                line.background("whiteui");
                line.margin(MARGIN);
                line.layout(l -> l.width(inner).color(Kit.INSET));
                line.labelWrap(Text.raw(buildChatPreviewText(model, local())), l -> l.width(inner - 2f * MARGIN));
            })).row();
            Badge shown = Badge.byId(model.previewBadge() != null && !model.previewBadge().isBlank()
                    ? model.previewBadge() : model.activeBadge());
            if (shown != null) {
                boolean worn = shown.id().equals(model.activeBadge());
                content.labelWrap(Text.raw(worn
                                ? "[lightgray]" + t("badge-state-active") + ": [lime]" + t(shown.nameKey()) + "[]"
                                : "[lightgray]" + t("player-settings-chat-preview-sample") + ": [accent]"
                                + t(shown.nameKey()) + "[]"),
                        l -> l.width(inner).padTop(TAB_GAP)).row();
            }
        })).row();

        boolean playerColor = usesPlayerBadgeSymbolColor(model.badgeSymbolColorMode());
        body.add(Kit.card(full, Tab.BADGES.accent, Iconc.pick + " " + t("player-settings-symbol-color-mode"), (content, inner) ->
                Kit.options(content, inner, COLOR_OPTION, "symbol", List.of(
                        new Option("[white]● []" + t("badge-menu-symbol-color-default"),
                                "action:symbol_color:default", !playerColor),
                        new Option("[#" + model.playerColorHex() + "]● []" + t("badge-menu-symbol-color-player-color"),
                                "action:symbol_color:player-color", playerColor))))).row();

        List<Badge> mine = new ArrayList<>();
        for (Badge badge : Badge.selectableManualBadges()) {
            if (model.unlockedBadges().contains(badge.id())) {
                mine.add(badge);
            }
        }
        boolean onlyMine = model.badgesFilter() == BadgesFilter.MY;
        body.add(Ui.table(filter -> {
            filter.layout(l -> l.padBottom(GAP - TAB_GAP));
            Kit.options(filter, full, FILTER_OPTION, "filter", List.of(
                    new Option(t("player-settings-badges-my") + " (" + mine.size() + ")",
                            "action:badges_filter:my", onlyMine),
                    new Option(t("player-settings-badges-all") + " (" + Badge.values().length + ")",
                            "action:badges_filter:all", !onlyMine)));
        })).row();

        if (onlyMine && mine.isEmpty()) {
            body.add(Kit.note(full, t("player-settings-badges-empty"))).row();
            return;
        }

        List<VNode> cards = new ArrayList<>();
        for (Badge badge : onlyMine ? mine : List.of(Badge.values())) {
            cards.add(badgeCard(screen, badge, model));
        }
        body.add(Kit.columns(screen, cards)).row();
    }

    // ------------------------------------------------------------------ pieces

    /**
     * A switch that takes effect when pressed: the whole row is the button, since a row is easier
     * to hit with a finger than a check box. Its texts are the bundle keys {@code key} and
     * {@code key-hint}.
     */
    private VNode toggle(Setting setting, boolean on, String key, float width, float padBottom) {
        float text = width - 2f * MARGIN - STATE_ICON - GAP;
        return Ui.buttonTable(setting.action(), row -> {
            row.style("flatTogglet").checked(on).margin(MARGIN);
            row.layout(l -> l.width(width).padBottom(padBottom));
            row.label(Text.raw(on ? "[lime]" + Iconc.ok + "[]" : "[gray]" + Iconc.cancel + "[]"),
                    l -> l.width(STATE_ICON).padRight(GAP));
            row.add(Ui.labelWrap(Text.raw((on ? "[white]" : "[lightgray]") + t(key) + "[]\n[gray]"
                    + t(key + "-hint") + "[]"), l -> l.width(text)));
        });
    }

    /** A badge: how it looks in chat, whether the player has it, and what can be done with it. */
    private VNode badgeCard(Screen screen, Badge badge, SettingsModel model) {
        float width = screen.card();
        float inner = width - 2f * PAD;
        boolean equipped = badge.id().equals(model.activeBadge());
        boolean unlocked = badge.system() || model.unlockedBadges().contains(badge.id());
        boolean previewing = badge.id().equals(model.previewBadge());

        String status;
        if (badge.system()) {
            status = model.isAdmin()
                    ? "[coral]" + Iconc.admin + " " + t("badge-state-system-active") + "[]"
                    : "[gray]" + Iconc.lock + " " + t("badge-state-system") + "[]";
        } else if (equipped) {
            status = "[lime]" + Iconc.ok + " " + t("badge-state-active") + "[]";
        } else if (unlocked) {
            status = "[accent]" + Iconc.lockOpen + " " + t("badge-state-unlocked") + "[]";
        } else {
            status = "[gray]" + Iconc.lock + " " + t("badge-state-locked") + "[]";
        }
        String description = t(badge.descriptionKey());

        List<VNode> actions = new ArrayList<>();
        boolean canWear = equipped || (unlocked && badge.selectable() && !badge.system());
        float buttonWidth = canWear ? (inner - TAB_GAP) / 2f : inner;
        if (previewing) {
            actions.add(Ui.button(Text.raw(t("player-settings-badge-previewing")), "action:preview_badge:" + badge.id(),
                    b -> b.style("flatBordert").disabled()
                            .layout(l -> l.width(buttonWidth).height(BUTTON_HEIGHT).padRight(canWear ? TAB_GAP : 0f))));
        } else {
            actions.add(Ui.button(Text.raw("[sky]" + Iconc.eye + " " + t("player-settings-badge-preview") + "[]"),
                    "action:preview_badge:" + badge.id(),
                    b -> b.style("flatBordert")
                            .layout(l -> l.width(buttonWidth).height(BUTTON_HEIGHT).padRight(canWear ? TAB_GAP : 0f))));
        }
        if (equipped) {
            actions.add(Ui.button(Text.raw("[scarlet]" + t("player-settings-badge-unequip") + "[]"),
                    "action:unequip_badge",
                    b -> b.style("flatBordert").layout(l -> l.width(buttonWidth).height(BUTTON_HEIGHT))));
        } else if (canWear) {
            actions.add(Ui.button(Text.raw("[accent]" + t("player-settings-badge-equip") + "[]"),
                    "action:equip_badge:" + badge.id(),
                    b -> b.style("flatBordert").layout(l -> l.width(buttonWidth).height(BUTTON_HEIGHT))));
        }

        return Ui.table(card -> {
            card.background("whiteui");
            card.layout(l -> l.width(width).padBottom(GAP).color(Kit.CARD));

            card.add(Ui.table(band -> {
                band.background("whiteui");
                band.margin(Kit.BAND_MARGIN);
                band.layout(l -> l.width(width).color(equipped ? Tab.BADGES.accent.band() : Kit.HEADER));
                band.labelWrap(Text.raw(badgeLabelWithColor(local(), badge, model.badgeSymbolColorMode(),
                        model.playerColorHex())), l -> l.width(width - 2f * Kit.BAND_MARGIN));
            })).row();

            card.labelWrap(Text.raw(description.isBlank() ? status : status + "\n[lightgray]" + description + "[]"),
                    l -> l.width(inner).padTop(GAP).padBottom(GAP)).row();
            card.add(Ui.table(row -> {
                row.layout(l -> l.width(inner).padBottom(PAD));
                actions.forEach(row::add);
            })).row();
        });
    }

    /** The name the player joined with, in white unless it brings its own colour. */
    private static String playerName(SettingsModel model) {
        String name = model.nickname() == null ? "" : model.nickname();
        return name.startsWith("[") ? name : "[white]" + name;
    }

    private static boolean isTranslatorOff(String code) {
        return code == null || code.isBlank() || "off".equalsIgnoreCase(code);
    }

    private Localization local() {
        return session != null ? session.locale() : null;
    }

    /** The text of {@code key} in the player's language. */
    private String t(String key) {
        Localization local = local();
        return local != null ? local.t(key) : key;
    }

    @Override
    public SettingsEvent parseEvent(MenuResult result) {
        if (result == null) return null;
        if (result.wasCancelled()) return new SettingsEvent.Close();

        String res = result.result;
        if (res == null) return null;

        if ("action:save".equals(res)) return new SettingsEvent.Save(result);
        if ("action:reset_nick".equals(res)) return new SettingsEvent.ResetNickname(result);
        if ("action:symbol_color:default".equals(res)) return new SettingsEvent.SelectSymbolColorMode("default");
        if ("action:symbol_color:player-color".equals(res)) return new SettingsEvent.SelectSymbolColorMode("player-color");
        if ("action:unequip_badge".equals(res)) return new SettingsEvent.UnequipBadge();

        if (res.startsWith("action:tab:")) {
            String tabStr = res.substring("action:tab:".length()).toUpperCase(Locale.ROOT);
            try {
                Tab tab = Tab.valueOf(tabStr);
                return new SettingsEvent.SelectTab(tab, result);
            } catch (IllegalArgumentException ignored) {}
        }

        if (res.startsWith("action:toggle:")) {
            String name = res.substring("action:toggle:".length()).toUpperCase(Locale.ROOT);
            try {
                return new SettingsEvent.Toggle(Setting.valueOf(name), result);
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
