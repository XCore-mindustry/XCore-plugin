package org.xcore.plugin.ui.menu;

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
 * Reactive player settings screen utilizing xcore-ui forms, lenses, and inline field controls.
 *
 * <p>Eliminates blocking modal {@code textInput} popups in Mindustry v160 by batching
 * text field and checkbox values into {@link MenuResult#values} upon discrete actions.
 */
public class PlayerSettingsUiController implements UiController<PlayerSettingsUiController.SettingsModel, PlayerSettingsUiController.SettingsEvent> {

    public static final SlotKey<Object> SLOT_FEEDBACK = SlotKey.of("slot_feedback");

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
            String feedbackMessage,
            boolean isSuccess
    ) {
        public SettingsModel withCustomNickname(String nick) {
            return new SettingsModel(targetUuid, nickname, nick, description, globalChatVisible, discordRelayVisible, leaderboard, activeBadge, language, feedbackMessage, isSuccess);
        }

        public SettingsModel withDescription(String desc) {
            return new SettingsModel(targetUuid, nickname, customNickname, desc, globalChatVisible, discordRelayVisible, leaderboard, activeBadge, language, feedbackMessage, isSuccess);
        }

        public SettingsModel withGlobalChatVisible(boolean visible) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, visible, discordRelayVisible, leaderboard, activeBadge, language, feedbackMessage, isSuccess);
        }

        public SettingsModel withDiscordRelayVisible(boolean visible) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible, visible, leaderboard, activeBadge, language, feedbackMessage, isSuccess);
        }

        public SettingsModel withLeaderboard(boolean lb) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible, discordRelayVisible, lb, activeBadge, language, feedbackMessage, isSuccess);
        }

        public SettingsModel withFeedback(String msg, boolean success) {
            return new SettingsModel(targetUuid, nickname, customNickname, description, globalChatVisible, discordRelayVisible, leaderboard, activeBadge, language, msg, success);
        }
    }

    public sealed interface SettingsEvent {
        record Save(MenuResult result) implements SettingsEvent {}
        record ResetNickname() implements SettingsEvent {}
        record OpenBadges() implements SettingsEvent {}
        record OpenLanguage() implements SettingsEvent {}
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
                "",
                false
        );
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

            case SettingsEvent.OpenBadges() -> {
                ctx.close();
                if (menu != null && session != null && session.player != null) {
                    menu.badges(session.player.uuid(), targetData);
                }
                yield UpdateResult.close(model);
            }

            case SettingsEvent.OpenLanguage() -> {
                ctx.close();
                if (menu != null && session != null && session.player != null) {
                    menu.languageSelectionMenu(session.player.uuid(), targetData, false);
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
            t.layout(l -> l.width(480f).pad(6f));

            // 1. Header with title
            t.add(Ui.table(h -> {
                h.layout(l -> l.growX().padBottom(8f));
                h.label(Text.t("player-menu-settings-title"), l -> l.align("left").growX());
            })).row();

            // 2. Profile input section
            t.add(Ui.table(p -> {
                p.background("button");
                p.margin(10f);
                p.layout(l -> l.growX().padBottom(8f));

                // Vanilla Name info row
                p.add(Ui.table(row -> {
                    row.layout(l -> l.growX().padBottom(6f));
                    row.label(Text.join(Text.t("player-settings-player-label"), Text.raw("  [white]" + model.nickname() + "[]")),
                            l -> l.align("left").growX());
                })).row();

                // Custom Nickname
                p.label(Text.t("player-menu-settings-customNickname"), l -> l.align("left").padBottom(2f)).row();
                p.field("field_nickname", f -> f
                        .value(model.customNickname())
                        .hint(Text.t("player-menu-settings-customNickname-message"))
                        .maxLength(40)
                        .layout(l -> l.growX().padBottom(6f))).row();

                // Description
                p.label(Text.t("player-menu-settings-description"), l -> l.align("left").padBottom(2f)).row();
                p.field("field_description", f -> f
                        .value(model.description())
                        .maxLength(200)
                        .layout(l -> l.growX())).row();
            })).row();

            // 3. Toggles section (chat & leaderboard)
            t.add(Ui.table(toggles -> {
                toggles.background("button");
                toggles.margin(10f);
                toggles.layout(l -> l.growX().padBottom(8f));

                toggles.check(Text.t("player-settings-global-chat"), c -> c
                        .id("check_global_chat")
                        .checked(model.globalChatVisible())
                        .layout(l -> l.align("left").growX().padBottom(4f))).row();

                toggles.check(Text.t("player-settings-discord-relay"), c -> c
                        .id("check_discord_relay")
                        .checked(model.discordRelayVisible())
                        .layout(l -> l.align("left").growX().padBottom(4f))).row();

                toggles.check(Text.t("player-settings-leaderboard"), c -> c
                        .id("check_leaderboard")
                        .checked(model.leaderboard())
                        .layout(l -> l.align("left").growX()));
            })).row();

            // 4. Submenus row (Badges & Language)
            t.add(Ui.table(sub -> {
                sub.layout(l -> l.growX().padBottom(8f));
                sub.button(Text.t("player-menu-settings-badges"), "action:badges", b -> b
                        .layout(l -> l.uniform().growX().height(36f).padRight(4f)));
                sub.button(Text.t("player-settings-language"), "action:language", b -> b
                        .layout(l -> l.uniform().growX().height(36f)));
            })).row();

            // 5. Dynamic Feedback Slot
            t.slot("slot_feedback", fb -> {
                fb.layout(l -> l.growX().minHeight(22f).padBottom(4f));
                if (model.feedbackMessage() != null && !model.feedbackMessage().isBlank()) {
                    fb.label(Text.raw(model.feedbackMessage()), l -> l.align("center").growX());
                }
            }).row();

            // 6. Action Footer Bar
            t.add(Ui.table(f -> {
                f.layout(l -> l.growX());
                f.button(Text.t("save"), "action:save", b -> b
                        .style("defaultt")
                        .layout(l -> l.uniform().growX().height(38f).padRight(4f)));
                f.button(Text.t("player-menu-settings-customNickname-reset"), "action:reset_nick", b -> b
                        .layout(l -> l.uniform().growX().height(38f).padRight(4f)));
                f.button(Text.t("close"), "action:close", b -> b
                        .layout(l -> l.uniform().growX().height(38f)));
            }));
        });
    }

    @Override
    public SettingsEvent parseEvent(MenuResult result) {
        if (result == null || result.result == null) return null;
        return switch (result.result) {
            case "action:save" -> new SettingsEvent.Save(result);
            case "action:reset_nick" -> new SettingsEvent.ResetNickname();
            case "action:badges" -> new SettingsEvent.OpenBadges();
            case "action:language" -> new SettingsEvent.OpenLanguage();
            case "action:close" -> new SettingsEvent.Close();
            default -> null;
        };
    }
}
