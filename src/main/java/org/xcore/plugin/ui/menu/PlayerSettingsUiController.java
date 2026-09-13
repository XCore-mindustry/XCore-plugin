package org.xcore.plugin.ui.menu;

import arc.util.Strings;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.ui.Lens;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;
import org.xcore.ui.form.FormSchema;
import org.xcore.ui.form.ValueCodec;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.SlotKey;
import org.xcore.ui.runtime.UiController;
import org.xcore.ui.runtime.UpdateResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Modern reactive player settings screen utilizing xcore-ui forms, inline fields,
 * and an in-place language combobox.
 *
 * <p>Eliminates modal {@code textInput} popups and multi-screen submenus by allowing
 * in-dialog editing of nickname, description, checkboxes, and language selection.
 */
public class PlayerSettingsUiController implements UiController<PlayerSettingsUiController.SettingsModel, PlayerSettingsUiController.SettingsEvent> {

    public static final SlotKey<Object> SLOT_FEEDBACK = SlotKey.of("slot_feedback");
    public static final SlotKey<Object> SLOT_LANG = SlotKey.of("slot_lang");

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
            new LanguageOption("be", "Беларуская")
    );

    private final PlayerMenu menu;
    private final PlayerProfileSettingsService profileSettings;
    private final Session session;
    private final PlayerData targetData;
    private final FormSchema<SettingsModel> schema;

    public PlayerSettingsUiController(PlayerMenu menu,
                                      PlayerProfileSettingsService profileSettings,
                                      Session session,
                                      PlayerData targetData) {
        this.menu = menu;
        this.profileSettings = profileSettings;
        this.session = session;
        this.targetData = targetData;
        this.schema = createSchema();
    }

    private FormSchema<SettingsModel> createSchema() {
        return new FormSchema<SettingsModel>()
                .bind("field_nickname",
                        Lens.of(SettingsModel::customNickname, SettingsModel::withCustomNickname),
                        ValueCodec.string(),
                        List.of())
                .bind("field_description",
                        Lens.of(SettingsModel::description, SettingsModel::withDescription),
                        ValueCodec.string(),
                        List.of())
                .bind("check_global_chat",
                        Lens.of(SettingsModel::globalChatVisible, SettingsModel::withGlobalChatVisible),
                        ValueCodec.bool(),
                        List.of())
                .bind("check_discord_relay",
                        Lens.of(SettingsModel::discordRelayVisible, SettingsModel::withDiscordRelayVisible),
                        ValueCodec.bool(),
                        List.of())
                .bind("check_leaderboard",
                        Lens.of(SettingsModel::leaderboard, SettingsModel::withLeaderboard),
                        ValueCodec.bool(),
                        List.of());
    }

    public record SettingsModel(
            String targetUuid,
            String nickname,
            String customNickname,
            String description,
            boolean globalChatVisible,
            boolean discordRelayVisible,
            boolean leaderboard,
            String activeBadge,
            String language,
            boolean langDropdownOpen,
            String feedbackMessage,
            boolean isSuccess
    ) {
        public SettingsModel withCustomNickname(String nick) {
            return new SettingsModel(targetUuid, nickname, nick, description, globalChatVisible, discordRelayVisible, leaderboard, activeBadge, language, langDropdownOpen, feedbackMessage, isSuccess);
        }

        public SettingsModel withDescription(String desc) {
            return new SettingsModel(targetUuid, nickname, customNickname, desc, globalChatVisible, discordRelayVisible, leaderboard, activeBadge, language, langDropdownOpen, feedbackMessage, isSuccess);
        }

        public SettingsModel withGlobalChatVisible(boolean visible) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, visible, discordRelayVisible, leaderboard, activeBadge, language, langDropdownOpen, feedbackMessage, isSuccess);
        }

        public SettingsModel withDiscordRelayVisible(boolean visible) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible, visible, leaderboard, activeBadge, language, langDropdownOpen, feedbackMessage, isSuccess);
        }

        public SettingsModel withLeaderboard(boolean lb) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible, discordRelayVisible, lb, activeBadge, language, langDropdownOpen, feedbackMessage, isSuccess);
        }

        public SettingsModel withLanguage(String lang) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible, discordRelayVisible, leaderboard, activeBadge, lang, langDropdownOpen, feedbackMessage, isSuccess);
        }

        public SettingsModel withLangDropdownOpen(boolean open) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible, discordRelayVisible, leaderboard, activeBadge, language, open, feedbackMessage, isSuccess);
        }

        public SettingsModel withFeedback(String msg, boolean success) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible, discordRelayVisible, leaderboard, activeBadge, language, langDropdownOpen, msg, success);
        }
    }

    public sealed interface SettingsEvent {
        record Save(MenuResult result) implements SettingsEvent {}
        record ResetNickname() implements SettingsEvent {}
        record OpenBadges() implements SettingsEvent {}
        record ToggleLanguageDropdown() implements SettingsEvent {}
        record SelectLanguage(String code) implements SettingsEvent {}
        record Close() implements SettingsEvent {}
    }

    public static SettingsModel createModel(Session session, PlayerData targetData) {
        Objects.requireNonNull(targetData, "targetData");
        String customNick = targetData.customNickname != null ? targetData.customNickname : "";
        String desc = targetData.description != null ? targetData.description : "";
        return new SettingsModel(
                targetData.uuid,
                targetData.nickname != null ? targetData.nickname : "",
                customNick,
                desc,
                targetData.globalChatVisible,
                targetData.discordRelayVisible,
                targetData.leaderboard,
                targetData.activeBadge != null ? targetData.activeBadge : "",
                targetData.language != null ? targetData.language : "auto",
                false,
                "",
                false
        );
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

    @Override
    public SettingsModel initialModel(Object context) {
        throw new UnsupportedOperationException("Open via menu.openSettingsUi(...) with pre-computed initial model");
    }

    @Override
    public UpdateResult<SettingsModel> update(SettingsModel model, SettingsEvent event, ControllerContext ctx) {
        return switch (event) {
            case SettingsEvent.Save(MenuResult result) -> {
                FormSchema.FormResult<SettingsModel> formResult = schema.apply(model, result);
                SettingsModel updated = formResult.model();

                String newNick = updated.customNickname() != null ? updated.customNickname().trim() : "";
                if (!newNick.isEmpty() && profileSettings != null) {
                    var validation = profileSettings.validateCustomNickname(newNick);
                    if (!validation.valid()) {
                        String errKey = validation.errorKey() != null ? validation.errorKey() : "error-nickname-invalid";
                        String localizedErr = session != null && session.locale() != null
                                ? session.locale().t(errKey, Map.of("max", validation.maxBytes()))
                                : errKey;
                        yield UpdateResult.patch(model.withFeedback("[scarlet]⚠ " + localizedErr + "[]", false), SLOT_FEEDBACK);
                    }
                }

                if (profileSettings != null) {
                    profileSettings.updateCustomNickname(targetData, newNick, true, true);
                    profileSettings.updateDescription(targetData, updated.description() != null ? updated.description().trim() : "");
                    profileSettings.updateGlobalChatVisible(targetData, updated.globalChatVisible());
                    profileSettings.updateDiscordRelayVisible(targetData, updated.discordRelayVisible());
                    profileSettings.updateLeaderboard(targetData, updated.leaderboard());
                    profileSettings.updateLanguage(targetData, updated.language());
                }

                String successMsg = session != null && session.locale() != null
                        ? session.locale().t("player-settings-saved")
                        : "Settings saved!";
                yield UpdateResult.patch(updated.withFeedback(successMsg, true), SLOT_FEEDBACK);
            }

            case SettingsEvent.ResetNickname() -> {
                if (profileSettings != null) {
                    profileSettings.updateCustomNickname(targetData, "", true, true);
                }
                String resetMsg = session != null && session.locale() != null
                        ? session.locale().t("player-settings-reset-feedback")
                        : "Custom nickname reset.";
                yield UpdateResult.rerender(model.withCustomNickname("").withFeedback(resetMsg, true));
            }

            case SettingsEvent.ToggleLanguageDropdown() ->
                    UpdateResult.patch(model.withLangDropdownOpen(!model.langDropdownOpen()), SLOT_LANG);

            case SettingsEvent.SelectLanguage(var code) ->
                    UpdateResult.patch(model.withLanguage(code).withLangDropdownOpen(false), SLOT_LANG);

            case SettingsEvent.OpenBadges() -> {
                ctx.close();
                if (menu != null && session != null && session.player != null) {
                    menu.badges(session.player.uuid(), targetData);
                }
                yield UpdateResult.close(model);
            }

            case SettingsEvent.Close() -> {
                ctx.close();
                yield UpdateResult.close(model);
            }
        };
    }

    @Override
    public VNode render(SettingsModel model) {
        return Ui.table(t -> {
            t.background("pane");
            t.margin(14f);
            t.layout(l -> l.width(520f).pad(6f));

            // 1. Centered Header with Accent title and underline
            t.add(Ui.table(h -> {
                h.layout(l -> l.growX().padBottom(4f));
                h.label(Text.t("player-menu-settings-title"), l -> l.align("center").growX());
            })).row();
            t.image("whiteui", l -> l.growX().height(3f).padBottom(10f).color("ffd37f")).row();

            // 2. Profile input section
            t.add(Ui.table(p -> {
                p.layout(l -> l.growX());

                // Vanilla Name info row with inline Reset button
                p.add(Ui.table(row -> {
                    row.layout(l -> l.growX().padBottom(4f));
                    row.label(Text.join(Text.t("player-settings-player-label"), Text.raw("  [white]" + model.nickname() + "[]")),
                            l -> l.align("left").growX());
                    row.button(Text.t("player-settings-reset-nick-btn"), "action:reset_nick", b -> b
                            .style("cleart")
                            .layout(l -> l.height(24f)));
                })).row();

                String rawHint = session != null && session.locale() != null
                        ? session.locale().t("player-menu-settings-customNickname-message")
                        : "Leave blank to reset";
                String cleanHint = Strings.stripColors(rawHint);

                p.label(Text.t("player-menu-settings-customNickname"), l -> l.align("left").padBottom(2f)).row();
                p.field("field_nickname", f -> f
                        .value(model.customNickname())
                        .hint(cleanHint)
                        .maxLength(40)
                        .layout(l -> l.growX().padBottom(8f))).row();

                // Description
                p.label(Text.t("player-menu-settings-description"), l -> l.align("left").padBottom(2f)).row();
                p.field("field_description", f -> f
                        .value(model.description())
                        .maxLength(200)
                        .layout(l -> l.growX()));
            })).row();

            // Divider line
            t.image("whiteui", l -> l.growX().height(2f).padTop(10f).padBottom(10f).color("454545")).row();

            // 3. Toggles section (clean left-aligned checkboxes directly on pane)
            t.add(Ui.table(toggles -> {
                toggles.layout(l -> l.align("left").growX());

                toggles.check(Text.t("player-settings-global-chat"), c -> c
                        .id("check_global_chat")
                        .checked(model.globalChatVisible())
                        .layout(l -> l.align("left").padBottom(6f))).row();

                toggles.check(Text.t("player-settings-discord-relay"), c -> c
                        .id("check_discord_relay")
                        .checked(model.discordRelayVisible())
                        .layout(l -> l.align("left").padBottom(6f))).row();

                toggles.check(Text.t("player-settings-leaderboard"), c -> c
                        .id("check_leaderboard")
                        .checked(model.leaderboard())
                        .layout(l -> l.align("left")));
            })).row();

            // Divider line
            t.image("whiteui", l -> l.growX().height(2f).padTop(10f).padBottom(10f).color("454545")).row();

            // 4. Preferences & Language Section
            t.add(Ui.table(pref -> {
                pref.layout(l -> l.growX());

                // Badges row
                pref.add(Ui.table(inner -> {
                    inner.layout(l -> l.growX().padBottom(6f));
                    String badgeTag = !model.activeBadge().isBlank() ? "  [gold][" + model.activeBadge() + "][]" : "  [gray][None][]";
                    inner.label(Text.join(Text.t("player-menu-settings-badges"), Text.raw(badgeTag)), l -> l.align("left").growX());
                    inner.button(Text.t("player-settings-edit-badges"), "action:badges", b -> b
                            .style("cleart")
                            .layout(l -> l.height(28f)));
                })).row();

                // Language Combobox Slot inside section
                pref.slot("slot_lang", langSlot -> {
                    langSlot.layout(l -> l.growX());

                    String currentLang = resolveLanguageDisplay(model.language());
                    String arrow = model.langDropdownOpen() ? "  ▲" : "  ▼";

                    langSlot.add(Ui.table(btnRow -> {
                        btnRow.layout(l -> l.growX());
                        btnRow.label(Text.t("player-settings-language"), l -> l.align("left").growX());
                        btnRow.button(Text.raw(currentLang + arrow), "action:toggle_lang", b -> b
                                .style("cleart")
                                .layout(l -> l.height(30f)));
                    })).row();

                    if (model.langDropdownOpen()) {
                        langSlot.add(Ui.table(opts -> {
                            opts.layout(l -> l.growX().padTop(6f));
                            int col = 0;
                            for (LanguageOption opt : AVAILABLE_LANGUAGES) {
                                boolean isSel = opt.code().equals(model.language());
                                opts.button(Text.raw(opt.displayName()), "action:select_lang:" + opt.code(), b -> b
                                        .style("togglet")
                                        .checked(isSel)
                                        .layout(l -> l.uniform().growX().height(32f).pad(2f)));
                                col++;
                                if (col % 2 == 0) {
                                    opts.row();
                                }
                            }
                        })).row();
                    }
                });
            })).row();

            // Divider line
            t.image("whiteui", l -> l.growX().height(2f).padTop(10f).padBottom(8f).color("454545")).row();

            // 5. Dynamic Feedback Slot
            t.slot("slot_feedback", fb -> {
                fb.layout(l -> l.growX().minHeight(20f).padBottom(4f));
                if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
                    fb.label(Text.raw(model.feedbackMessage()), l -> l.align("center").growX());
                }
            }).row();

            // 6. Action Footer Bar
            t.add(Ui.table(f -> {
                f.layout(l -> l.growX().padTop(2f));
                f.button(Text.join(Text.raw("[accent]"), Text.t("save")), "action:save", b -> b
                        .style("cleart")
                        .layout(l -> l.growX().height(38f)));
            }));
        });
    }

    @Override
    public SettingsEvent parseEvent(MenuResult result) {
        if (result == null || result.result == null) return null;
        if ("action:save".equals(result.result)) return new SettingsEvent.Save(result);
        if ("action:reset_nick".equals(result.result)) return new SettingsEvent.ResetNickname();
        if ("action:badges".equals(result.result)) return new SettingsEvent.OpenBadges();
        if ("action:toggle_lang".equals(result.result)) return new SettingsEvent.ToggleLanguageDropdown();
        if (result.result.startsWith("action:select_lang:")) {
            return new SettingsEvent.SelectLanguage(result.result.substring("action:select_lang:".length()));
        }
        if ("action:close".equals(result.result)) return new SettingsEvent.Close();
        return null;
    }
}
