package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UpdateResult;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerSettingsUiControllerTest {

    @SuppressWarnings("unchecked")
    private Session createTestSession(String uuid, boolean mobile) {
        PlayerData data = new PlayerData(uuid, true);
        data.nickname = "TestUser";
        data.customNickname = "EpicGamer";
        data.description = "Builder of defenses";
        data.globalChatVisible = true;
        data.discordRelayVisible = false;
        data.leaderboard = true;
        data.unlockedBadges = Set.of(Badge.DEVELOPER.id(), Badge.MAP_MAKER.id());
        data.activeBadge = Badge.DEVELOPER.id();
        data.badgeSymbolColorMode = "default";
        data.language = "ru";
        data.translatorLanguage = "off";

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        Player player = Player.create();
        player.con = mock(NetConnection.class);
        player.con.mobile = mobile;

        return new Session(
                new TomlSecretsConfig(),
                bundle,
                null,
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }

    @Test
    @DisplayName("createModel initializes settings from PlayerData with correct default tab")
    void createModel_initializesFromPlayerData() {
        Session session = createTestSession("uuid-1", false);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        assertThat(model.targetUuid()).isEqualTo("uuid-1");
        assertThat(model.nickname()).isEqualTo("TestUser");
        assertThat(model.customNickname()).isEqualTo("EpicGamer");
        assertThat(model.description()).isEqualTo("Builder of defenses");
        assertThat(model.globalChatVisible()).isTrue();
        assertThat(model.discordRelayVisible()).isFalse();
        assertThat(model.leaderboard()).isTrue();
        assertThat(model.tab()).isEqualTo(PlayerSettingsUiController.Tab.PROFILE);
        assertThat(model.unlockedBadges()).contains(Badge.DEVELOPER.id(), Badge.MAP_MAKER.id());
        assertThat(model.activeBadge()).isEqualTo(Badge.DEVELOPER.id());
    }

    @Test
    @DisplayName("render compiles Profile tab with 740 width desktop metrics and inputs")
    void render_compilesProfileTabDesktop() {
        Session session = createTestSession("uuid-1", false);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("maxWidth: 760");
        assertThat(dsl).contains("maxHeight: 460");

        // Tabs
        assertThat(dsl).contains("action:tab:profile");
        assertThat(dsl).contains("action:tab:chat_lang");
        assertThat(dsl).contains("action:tab:badges");

        // Profile fields
        assertThat(dsl).contains("id: field_nickname");
        assertThat(dsl).contains("id: field_description");
        assertThat(dsl).contains("id: check_leaderboard");
        assertThat(dsl).contains("action:reset_nick");
        assertThat(dsl).contains("action:close");
        assertThat(dsl).contains("action:save");
    }

    @Test
    @DisplayName("render emits the same caps for a mobile client instead of a narrower guess")
    void render_compilesSameCapsForMobileClient() {
        Session session = createTestSession("uuid-1", true);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        // A mobile flag on the session must not change the tree: the client sizes itself.
        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("maxWidth: 760");
        assertThat(dsl).contains("maxHeight: 460");
        assertThat(dsl).doesNotContain("width: 520");
    }

    @Test
    @DisplayName("render compiles Chat and Language tab with checkboxes and language grids")
    void render_compilesChatLangTab() {
        Session session = createTestSession("uuid-1", false);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, session.data, PlayerSettingsUiController.Tab.CHAT_LANG
        );
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("id: check_global_chat");
        assertThat(dsl).contains("id: check_discord_relay");
        assertThat(dsl).contains("action:select_lang:ru");
        assertThat(dsl).contains("action:select_lang:en");
        assertThat(dsl).contains("action:select_translator:off");
        assertThat(dsl).contains("action:select_translator:uk_UA");
    }

    @Test
    @DisplayName("render compiles Badges tab with preview, color mode toggles, and badge cards")
    void render_compilesBadgesTab() {
        Session session = createTestSession("uuid-1", false);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, session.data, PlayerSettingsUiController.Tab.BADGES
        );
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);

        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("action:toggle_symbol_color");
        assertThat(dsl).contains("action:badges_filter:my");
        assertThat(dsl).contains("action:badges_filter:all");
        assertThat(dsl).contains("action:unequip_badge");
        assertThat(dsl).contains("action:preview_badge:" + Badge.MAP_MAKER.id());
        assertThat(dsl).contains("action:equip_badge:" + Badge.MAP_MAKER.id());
    }

    @Test
    @DisplayName("PreviewBadge updates previewBadge in model and rerenders chat preview")
    void previewBadge_updatesPreviewInModel() {
        Session session = createTestSession("uuid-1", false);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, session.data, PlayerSettingsUiController.Tab.BADGES
        );

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.PreviewBadge(Badge.MAP_MAKER.id()), null
        );

        assertThat(result.model().previewBadge()).isEqualTo(Badge.MAP_MAKER.id());
        assertThat(result.fullRerender()).isTrue();
    }

    @Test
    @DisplayName("buildChatPreviewText preserves color tags in nickname without breaking markup")
    void buildChatPreviewText_preservesColoredNickname() {
        Session session = createTestSession("uuid-1", false);
        session.data.nickname = "[#2CABFEFF]mizoa";
        session.data.customNickname = "";
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, session.data, PlayerSettingsUiController.Tab.BADGES
        );

        String preview = PlayerSettingsUiController.buildChatPreviewText(model, null);

        assertThat(preview).contains("[#2CABFEFF]mizoa");
        assertThat(preview).doesNotContain("[[#2CABFEFF]");
        assertThat(preview).contains("Hello world!");
    }

    @Test
    @DisplayName("SelectTab preserves form input values typed on previous tab")
    void selectTab_preservesFormValues() {
        Session session = createTestSession("uuid-1", false);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        MenuResult tabResult = new MenuResult("action:tab:chat_lang");
        tabResult.values.put("field_nickname", "UpdatedNick");
        tabResult.values.put("field_description", "UpdatedDesc");
        tabResult.values.put("check_leaderboard", false);

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.SelectTab(PlayerSettingsUiController.Tab.CHAT_LANG, tabResult), null
        );

        assertThat(result.model().tab()).isEqualTo(PlayerSettingsUiController.Tab.CHAT_LANG);
        assertThat(result.model().customNickname()).isEqualTo("UpdatedNick");
        assertThat(result.model().description()).isEqualTo("UpdatedDesc");
        assertThat(result.model().leaderboard()).isFalse();
        assertThat(result.fullRerender()).isTrue();
    }

    @Test
    @DisplayName("update EquipBadge and UnequipBadge updates active badge and persists via service")
    void update_equipAndUnequipBadge_persists() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, session.data, PlayerSettingsUiController.Tab.BADGES
        );

        // Equip map maker
        UpdateResult<PlayerSettingsUiController.SettingsModel> equipResult = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.EquipBadge(Badge.MAP_MAKER.id()), null
        );
        verify(profileSettings).updateActiveBadge(eq(session.data), eq(Badge.MAP_MAKER.id()), eq(true), eq(true));
        assertThat(equipResult.model().activeBadge()).isEqualTo(Badge.MAP_MAKER.id());

        // Unequip
        UpdateResult<PlayerSettingsUiController.SettingsModel> unequipResult = controller.update(
                equipResult.model(), new PlayerSettingsUiController.SettingsEvent.UnequipBadge(), null
        );
        verify(profileSettings).updateActiveBadge(eq(session.data), eq(""), eq(true), eq(true));
        assertThat(unequipResult.model().activeBadge()).isEmpty();
    }

    @Test
    @DisplayName("update ToggleSymbolColorMode alternates between default and player-color")
    void update_toggleSymbolColorMode_alternates() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, session.data, PlayerSettingsUiController.Tab.BADGES
        );

        UpdateResult<PlayerSettingsUiController.SettingsModel> res1 = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.ToggleSymbolColorMode(), null
        );
        verify(profileSettings).updateBadgeSymbolColorMode(eq(session.data), eq("player-color"), eq(true), eq(true));
        assertThat(res1.model().badgeSymbolColorMode()).isEqualTo("player-color");

        UpdateResult<PlayerSettingsUiController.SettingsModel> res2 = controller.update(
                res1.model(), new PlayerSettingsUiController.SettingsEvent.ToggleSymbolColorMode(), null
        );
        verify(profileSettings).updateBadgeSymbolColorMode(eq(session.data), eq("default"), eq(true), eq(true));
        assertThat(res2.model().badgeSymbolColorMode()).isEqualTo("default");
    }

    @Test
    @DisplayName("update on Save applies form schema and persists via service")
    void update_save_appliesFormSchemaAndPersists() {
        Session session = createTestSession("uuid-1", false);
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

        assertThat(updateResult.model().feedbackMessage()).contains("player-settings-saved");
    }

    @Test
    @DisplayName("update on Save with invalid nickname returns error feedback without persisting")
    void update_save_invalidNicknameReturnsFeedback() {
        Session session = createTestSession("uuid-1", false);
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
        assertThat(updateResult.model().feedbackMessage()).contains("error-nickname-too-long");
    }

    @Test
    @DisplayName("update on ResetNickname clears custom nickname")
    void update_resetNickname_clearsNickname() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        UpdateResult<PlayerSettingsUiController.SettingsModel> updateResult = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.ResetNickname(new MenuResult("action:reset_nick")), null
        );

        verify(profileSettings).updateCustomNickname(eq(session.data), eq(""), eq(true), eq(true));
        assertThat(updateResult.model().customNickname()).isEmpty();
        assertThat(updateResult.fullRerender()).isTrue();
    }

    @Test
    @DisplayName("update on Close invokes controllerContext.close")
    void update_close_invokesContextClose() {
        Session session = createTestSession("uuid-1", false);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.Close(), null
        );

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

        MenuResult tabChatRes = new MenuResult("action:tab:chat_lang");
        assertThat(controller.parseEvent(tabChatRes)).isInstanceOf(PlayerSettingsUiController.SettingsEvent.SelectTab.class);

        MenuResult filterAllRes = new MenuResult("action:badges_filter:all");
        assertThat(controller.parseEvent(filterAllRes)).isEqualTo(
                new PlayerSettingsUiController.SettingsEvent.SelectBadgesFilter(PlayerSettingsUiController.BadgesFilter.ALL)
        );

        MenuResult equipRes = new MenuResult("action:equip_badge:developer");
        assertThat(controller.parseEvent(equipRes)).isEqualTo(
                new PlayerSettingsUiController.SettingsEvent.EquipBadge("developer")
        );

        MenuResult unequipRes = new MenuResult("action:unequip_badge");
        assertThat(controller.parseEvent(unequipRes)).isInstanceOf(PlayerSettingsUiController.SettingsEvent.UnequipBadge.class);

        MenuResult selectLangRes = new MenuResult("action:select_lang:uk_UA");
        assertThat(controller.parseEvent(selectLangRes)).isEqualTo(new PlayerSettingsUiController.SettingsEvent.SelectLanguage("uk_UA", selectLangRes));

        MenuResult selectTransRes = new MenuResult("action:select_translator:en");
        assertThat(controller.parseEvent(selectTransRes)).isEqualTo(new PlayerSettingsUiController.SettingsEvent.SelectTranslatorLanguage("en", selectTransRes));

        MenuResult closeRes = new MenuResult("action:close");
        assertThat(controller.parseEvent(closeRes)).isInstanceOf(PlayerSettingsUiController.SettingsEvent.Close.class);

        MenuResult cancelledRes = new MenuResult((String) null);
        assertThat(controller.parseEvent(cancelledRes)).isInstanceOf(PlayerSettingsUiController.SettingsEvent.Close.class);
    }

    @Test
    @DisplayName("equipBadge rejects unowned badge and does not persist")
    void equipBadge_rejectsUnownedBadge() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, session.data, PlayerSettingsUiController.Tab.BADGES
        );

        // VETERAN is not in session.data.unlockedBadges (which has DEVELOPER, MAP_MAKER)
        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.EquipBadge(Badge.VETERAN.id()), null
        );

        verify(profileSettings, never()).updateActiveBadge(any(), anyString(), anyBoolean(), anyBoolean());
        assertThat(result.model().feedbackMessage()).contains("error-badge-not-unlocked");
    }

    @Test
    @DisplayName("equipBadge rejects system admin badge even if requested")
    void equipBadge_rejectsSystemBadge() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, session.data, PlayerSettingsUiController.Tab.BADGES
        );

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.EquipBadge(Badge.ADMIN.id()), null
        );

        verify(profileSettings, never()).updateActiveBadge(any(), anyString(), anyBoolean(), anyBoolean());
        assertThat(result.model().feedbackMessage()).contains("error-badge-not-unlocked");
    }

    @Test
    @DisplayName("createModel does not grant admin status to target player when viewer is admin")
    void createModel_doesNotSpoofAdmin_whenViewerIsAdmin() {
        Session session = createTestSession("admin-uuid", false);
        session.player.admin = true;
        session.data.admin = true;

        PlayerData regularTarget = new PlayerData("target-uuid", true);
        regularTarget.admin = false;
        regularTarget.nickname = "NormalPlayer";

        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, regularTarget, PlayerSettingsUiController.Tab.BADGES
        );

        assertThat(model.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("SelectLanguage preserves form input values typed on previous tab")
    void selectLanguage_preservesFormValues() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        MenuResult langResult = new MenuResult("action:select_lang:uk_UA");
        langResult.values.put("field_nickname", "TypedNick");
        langResult.values.put("field_description", "TypedDesc");

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.SelectLanguage("uk_UA", langResult), null
        );

        assertThat(result.model().customNickname()).isEqualTo("TypedNick");
        assertThat(result.model().description()).isEqualTo("TypedDesc");
        assertThat(result.model().language()).isEqualTo("uk_UA");
    }
}
