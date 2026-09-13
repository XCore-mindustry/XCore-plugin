package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UpdateResult;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerSettingsUiControllerTest {

    @SuppressWarnings("unchecked")
    private Session createTestSession(String uuid) {
        PlayerData data = new PlayerData(uuid, true);
        data.nickname = "TestUser";
        data.customNickname = "EpicGamer";
        data.description = "Builder of defenses";
        data.globalChatVisible = true;
        data.discordRelayVisible = false;
        data.leaderboard = true;

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        return new Session(
                new TomlSecretsConfig(),
                bundle,
                null,
                mock(PlayerDataRepository.class),
                null,
                data
        );
    }

    @Test
    @DisplayName("createModel initializes settings from PlayerData")
    void createModel_initializesFromPlayerData() {
        Session session = createTestSession("uuid-1");
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        assertThat(model.targetUuid()).isEqualTo("uuid-1");
        assertThat(model.nickname()).isEqualTo("TestUser");
        assertThat(model.customNickname()).isEqualTo("EpicGamer");
        assertThat(model.description()).isEqualTo("Builder of defenses");
        assertThat(model.globalChatVisible()).isTrue();
        assertThat(model.discordRelayVisible()).isFalse();
        assertThat(model.leaderboard()).isTrue();
    }

    @Test
    @DisplayName("render compiles VNode tree with field inputs and checkboxes")
    void render_compilesVNodeTreeWithFieldsAndCheckboxes() {
        Session session = createTestSession("uuid-1");
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        // Background
        assertThat(dsl).contains("background: pane");

        // Profile fields
        assertThat(dsl).contains("id: field_nickname");
        assertThat(dsl).contains("id: field_description");

        // Checkboxes
        assertThat(dsl).contains("id: check_global_chat");
        assertThat(dsl).contains("id: check_discord_relay");
        assertThat(dsl).contains("id: check_leaderboard");

        // Actions
        assertThat(dsl).contains("action:save");
        assertThat(dsl).contains("action:reset_nick");
        assertThat(dsl).contains("action:close");

        // Language Combobox slot
        assertThat(dsl).contains("id: slot_lang");
        assertThat(dsl).contains("action:toggle_lang");

        // Feedback slot
        assertThat(dsl).contains("id: slot_feedback");
    }

    @Test
    @DisplayName("toggleLanguageDropdown patches slot_lang and inverts open state")
    void toggleLanguageDropdown_patchesSlotLang() {
        Session session = createTestSession("uuid-1");
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.ToggleLanguageDropdown(), null
        );

        assertThat(result.model().langDropdownOpen()).isTrue();
        assertThat(result.dirtySlots()).containsExactly(PlayerSettingsUiController.SLOT_LANG);
        assertThat(result.fullRerender()).isFalse();
    }

    @Test
    @DisplayName("selectLanguage patches slot_lang, sets language and closes dropdown")
    void selectLanguage_patchesSlotLangAndUpdatesLanguage() {
        Session session = createTestSession("uuid-1");
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data)
                .withLangDropdownOpen(true);

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.SelectLanguage("uk_UA"), null
        );

        assertThat(result.model().language()).isEqualTo("uk_UA");
        assertThat(result.model().langDropdownOpen()).isFalse();
        assertThat(result.dirtySlots()).containsExactly(PlayerSettingsUiController.SLOT_LANG);
        assertThat(result.fullRerender()).isFalse();
    }

    @Test
    @DisplayName("render with langDropdownOpen=true renders language option buttons")
    void render_withLangDropdownOpen_rendersOptionButtons() {
        Session session = createTestSession("uuid-1");
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data)
                .withLangDropdownOpen(true);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("action:select_lang:uk_UA");
        assertThat(dsl).contains("action:select_lang:ru");
        assertThat(dsl).contains("action:select_lang:en");
        assertThat(dsl).contains("Українська");
    }

    @Test
    @DisplayName("update on Save applies form schema and updates target data via service")
    void update_save_appliesFormSchemaAndPersists() {
        Session session = createTestSession("uuid-1");
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        when(profileSettings.validateCustomNickname(anyString()))
                .thenReturn(PlayerProfileSettingsService.NicknameValidationResult.ok());

        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        MenuResult result = new MenuResult("action:save");
        result.values.put("field_nickname", "NewNick");
        result.values.put("field_description", "New Description");
        result.values.put("check_global_chat", false);
        result.values.put("check_discord_relay", true);
        result.values.put("check_leaderboard", false);

        UpdateResult<PlayerSettingsUiController.SettingsModel> updateResult = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.Save(result), null
        );

        verify(profileSettings).updateCustomNickname(eq(session.data), eq("NewNick"), eq(true), eq(true));
        verify(profileSettings).updateDescription(eq(session.data), eq("New Description"));
        verify(profileSettings).updateGlobalChatVisible(eq(session.data), eq(false));
        verify(profileSettings).updateDiscordRelayVisible(eq(session.data), eq(true));
        verify(profileSettings).updateLeaderboard(eq(session.data), eq(false));

        assertThat(updateResult.dirtySlots()).containsExactly(PlayerSettingsUiController.SLOT_FEEDBACK);
        assertThat(updateResult.model().feedbackMessage()).contains("player-settings-saved");
    }

    @Test
    @DisplayName("update on Save with invalid nickname returns error feedback without persisting")
    void update_save_invalidNicknameReturnsFeedback() {
        Session session = createTestSession("uuid-1");
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        when(profileSettings.validateCustomNickname("TooLongNickname"))
                .thenReturn(PlayerProfileSettingsService.NicknameValidationResult.tooLong(40));

        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        MenuResult result = new MenuResult("action:save");
        result.values.put("field_nickname", "TooLongNickname");
        result.values.put("field_description", "Desc");

        UpdateResult<PlayerSettingsUiController.SettingsModel> updateResult = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.Save(result), null
        );

        verify(profileSettings, never()).updateCustomNickname(any(), any(), anyBoolean(), anyBoolean());
        assertThat(updateResult.dirtySlots()).containsExactly(PlayerSettingsUiController.SLOT_FEEDBACK);
        assertThat(updateResult.model().feedbackMessage()).contains("error-nickname-too-long");
    }

    @Test
    @DisplayName("update on ResetNickname clears custom nickname")
    void update_resetNickname_clearsNickname() {
        Session session = createTestSession("uuid-1");
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        UpdateResult<PlayerSettingsUiController.SettingsModel> updateResult = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.ResetNickname(), null
        );

        verify(profileSettings).updateCustomNickname(eq(session.data), eq(""), eq(true), eq(true));
        assertThat(updateResult.model().customNickname()).isEmpty();
        assertThat(updateResult.fullRerender()).isTrue();
    }

    @Test
    @DisplayName("update on Close invokes controllerContext.close")
    void update_close_invokesContextClose() {
        Session session = createTestSession("uuid-1");
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        AtomicBoolean closed = new AtomicBoolean(false);
        ControllerContext ctx = new ControllerContext() {
            @Override
            public String playerId() {
                return "uuid-1";
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.Close(), ctx
        );

        assertThat(closed.get()).isTrue();
        assertThat(result.close()).isTrue();
    }

    @Test
    @DisplayName("parseEvent correctly routes button actions")
    void parseEvent_routesActions() {
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, null, null);

        MenuResult saveRes = new MenuResult("action:save");
        assertThat(controller.parseEvent(saveRes)).isInstanceOf(PlayerSettingsUiController.SettingsEvent.Save.class);

        MenuResult resetRes = new MenuResult("action:reset_nick");
        assertThat(controller.parseEvent(resetRes)).isInstanceOf(PlayerSettingsUiController.SettingsEvent.ResetNickname.class);

        MenuResult badgesRes = new MenuResult("action:badges");
        assertThat(controller.parseEvent(badgesRes)).isInstanceOf(PlayerSettingsUiController.SettingsEvent.OpenBadges.class);

        MenuResult toggleLangRes = new MenuResult("action:toggle_lang");
        assertThat(controller.parseEvent(toggleLangRes)).isInstanceOf(PlayerSettingsUiController.SettingsEvent.ToggleLanguageDropdown.class);

        MenuResult selectLangRes = new MenuResult("action:select_lang:uk_UA");
        assertThat(controller.parseEvent(selectLangRes)).isEqualTo(new PlayerSettingsUiController.SettingsEvent.SelectLanguage("uk_UA"));

        MenuResult closeRes = new MenuResult("action:close");
        assertThat(controller.parseEvent(closeRes)).isInstanceOf(PlayerSettingsUiController.SettingsEvent.Close.class);
    }
}
