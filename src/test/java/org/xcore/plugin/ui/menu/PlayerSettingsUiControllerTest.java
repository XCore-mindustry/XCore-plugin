package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import arc.util.io.Writes;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.enums.IdentityDisplayMode;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.plugin.ui.kit.Screen;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VButton;
import org.xcore.ui.VButtonTable;
import org.xcore.ui.VCheck;
import org.xcore.ui.VField;
import org.xcore.ui.VLabel;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.VNodes;
import org.xcore.ui.VTable;
import org.xcore.ui.runtime.ControllerContext;
import org.xcore.ui.runtime.UpdateResult;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerSettingsUiControllerTest {

    private static Bundle realBundle;

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


    /** The compiled dialog as the client's DSL, with bundle keys in place of texts. */
    private String dsl(boolean mobile, PlayerSettingsUiController.Tab tab) {
        Session session = createTestSession("uuid-1", mobile);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);
        VNode root = controller.render(PlayerSettingsUiController.createModel(session, session.data, tab));
        return UiDslWriter.write(new VNodeCompiler(LocalizerResolver.IDENTITY).compile(root));
    }

    /** A controller that renders with the real texts of {@code language}. */
    private PlayerSettingsUiController translated(String language) {
        Session session = createTestSession("uuid-1", false);
        session.localization = new Localization(realBundle, Locale.forLanguageTag(language));
        return new PlayerSettingsUiController(null, null, session, session.data);
    }

    /** Every tab, the badges both filtered and not, each with a feedback line on top. */
    private List<PlayerSettingsUiController.SettingsModel> everyPage(String language) {
        Session session = createTestSession("uuid-1", false);
        List<PlayerSettingsUiController.SettingsModel> pages = new ArrayList<>();
        for (PlayerSettingsUiController.Tab tab : PlayerSettingsUiController.Tab.values()) {
            pages.add(PlayerSettingsUiController.createModel(session, session.data, tab)
                    .withFeedback("[scarlet]feedback[]", false));
        }
        pages.add(PlayerSettingsUiController.createModel(session, session.data, PlayerSettingsUiController.Tab.BADGES)
                .withBadgesFilter(PlayerSettingsUiController.BadgesFilter.ALL));
        // The username as a field, and as a settled line of the longest name there can be.
        pages.add(PlayerSettingsUiController.createModel(session, session.data).withCanChangeUsername(true));
        pages.add(PlayerSettingsUiController.createModel(session, session.data)
                .withUsername("a_username_of_the_longest_kind_1"));
        return pages;
    }

    @BeforeAll
    static void loadBundle() {
        realBundle = Bundle.INSTANCE;
        realBundle.addSource(new arc.files.Fi("src/main/resources/bundles"));
        realBundle.addLocaleAlias("uk", "uk_UA");
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
    @DisplayName("render lays the dialog out once per screen class, each under its own condition")
    void render_laysOutEveryScreenClass() {
        String dsl = dsl(false, PlayerSettingsUiController.Tab.PROFILE);

        assertThat(dsl).contains("condition: \"width >= 800\"");
        assertThat(dsl).contains("condition: \"width < 800\"");
        assertThat(dsl).contains("condition: \"width >= 490\"");
        assertThat(dsl).contains("condition: \"width < 490\"");
        assertThat(dsl).contains("background: pane");
    }

    @Test
    @DisplayName("render compiles Profile tab with the text fields, the save button and the leaderboard switch")
    void render_compilesProfileTab() {
        String dsl = dsl(false, PlayerSettingsUiController.Tab.PROFILE);

        assertThat(dsl).contains("action:tab:profile");
        assertThat(dsl).contains("action:tab:chat");
        assertThat(dsl).contains("action:tab:language");
        assertThat(dsl).contains("action:tab:badges");

        assertThat(dsl).contains("id: field_nickname");
        assertThat(dsl).contains("id: field_description");
        assertThat(dsl).contains("action:reset_nick");
        assertThat(dsl).contains("action:save");
        assertThat(dsl).contains("action:toggle:leaderboard");
        // The client's dialog has its own button to close it.
        assertThat(dsl).doesNotContain("action:close");
    }

    @Test
    @DisplayName("render emits the same tree for a mobile client: the client picks its layout, not the server")
    void render_isTheSameForMobileClient() {
        assertThat(dsl(true, PlayerSettingsUiController.Tab.PROFILE))
                .isEqualTo(dsl(false, PlayerSettingsUiController.Tab.PROFILE));
    }

    @Test
    @DisplayName("render compiles Chat tab with the chat switches and the translator languages")
    void render_compilesChatTab() {
        String dsl = dsl(false, PlayerSettingsUiController.Tab.CHAT);

        assertThat(dsl).contains("action:toggle:global_chat");
        assertThat(dsl).contains("action:toggle:discord_relay");
        assertThat(dsl).contains("action:select_translator:off");
        assertThat(dsl).contains("action:select_translator:uk_UA");
        assertThat(dsl).doesNotContain("action:select_lang:");
    }

    @Test
    @DisplayName("render compiles Language tab with every interface language")
    void render_compilesLanguageTab() {
        String dsl = dsl(false, PlayerSettingsUiController.Tab.LANGUAGE);

        assertThat(dsl).contains("action:select_lang:auto");
        for (var language : PlayerSettingsUiController.AVAILABLE_LANGUAGES) {
            assertThat(dsl).contains("action:select_lang:" + language.code());
        }
        assertThat(dsl).doesNotContain("action:select_translator:");
    }

    @Test
    @DisplayName("render compiles Badges tab with preview, symbol colour choices, and badge cards")
    void render_compilesBadgesTab() {
        String dsl = dsl(false, PlayerSettingsUiController.Tab.BADGES);

        assertThat(dsl).contains("action:symbol_color:default");
        assertThat(dsl).contains("action:symbol_color:player-color");
        assertThat(dsl).contains("action:badges_filter:my");
        assertThat(dsl).contains("action:badges_filter:all");
        assertThat(dsl).contains("action:unequip_badge");
        assertThat(dsl).contains("action:preview_badge:" + Badge.MAP_MAKER.id());
        assertThat(dsl).contains("action:equip_badge:" + Badge.MAP_MAKER.id());
    }

    /**
     * The client gives a wrapped label the width of its whole line and a wrap table the width of
     * all its cells, so one of either without a width stretches the dialog off a phone's screen.
     */
    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en"})
    @DisplayName("nothing in the dialog is left to find its own width")
    void nothingIsLeftToFindItsOwnWidth(String language) {
        for (PlayerSettingsUiController.SettingsModel model : everyPage(language)) {
            PlayerSettingsUiController controller = translated(language);
            for (Screen screen : Screen.ALL) {
                LayoutAssert.assertLaidOut(controller.window(model, screen), screen);
            }
        }
    }

    /** A dialog travels as one packet, and the client reads a packet into a 32 KB buffer. */
    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en"})
    @DisplayName("every page of the dialog fits one packet")
    void everyPageFitsOnePacket(String language) throws Exception {
        for (PlayerSettingsUiController.SettingsModel model : everyPage(language)) {
            VNode root = translated(language).render(model);
            var bytes = new ByteArrayOutputStream();
            try (var writes = new Writes(new DataOutputStream(bytes))) {
                new VNodeCompiler(LocalizerResolver.IDENTITY).compile(root).write(writes);
            }
            assertThat(bytes.size())
                    .as("%s (%s) in %s", model.tab(), model.badgesFilter(), language)
                    .isLessThan(24_000);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "uk", "en"})
    @DisplayName("every text of the dialog is translated")
    void everyTextIsTranslated(String language) {
        for (PlayerSettingsUiController.SettingsModel model : everyPage(language)) {
            for (VNode node : VNodes.walk(translated(language).render(model))) {
                String text = switch (node) {
                    case VLabel label -> label.text().resolve(null);
                    case VButton button -> button.text().resolve(null);
                    default -> "";
                };
                assertThat(text).doesNotContain("player-settings-").doesNotContain("badge-state-")
                        .doesNotContain("player-menu-settings-");
            }
        }
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

        String preview = PlayerSettingsUiController.buildChatPreviewText(model, LayoutAssert.localization("en"));

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

        MenuResult tabResult = new MenuResult("action:tab:chat");
        tabResult.values.put("field_nickname", "UpdatedNick");
        tabResult.values.put("field_description", "UpdatedDesc");

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.SelectTab(PlayerSettingsUiController.Tab.CHAT, tabResult), null
        );

        assertThat(result.model().tab()).isEqualTo(PlayerSettingsUiController.Tab.CHAT);
        assertThat(result.model().customNickname()).isEqualTo("UpdatedNick");
        assertThat(result.model().description()).isEqualTo("UpdatedDesc");
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
    @DisplayName("update SelectSymbolColorMode persists a new mode and ignores the one already chosen")
    void update_selectSymbolColorMode_persistsOnlyAChange() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(
                session, session.data, PlayerSettingsUiController.Tab.BADGES
        );

        UpdateResult<PlayerSettingsUiController.SettingsModel> same = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.SelectSymbolColorMode("default"), null
        );
        verify(profileSettings, never()).updateBadgeSymbolColorMode(any(), anyString(), anyBoolean(), anyBoolean());
        assertThat(same.isNoop()).isTrue();

        UpdateResult<PlayerSettingsUiController.SettingsModel> changed = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.SelectSymbolColorMode("player-color"), null
        );
        verify(profileSettings).updateBadgeSymbolColorMode(eq(session.data), eq("player-color"), eq(true), eq(true));
        assertThat(changed.model().badgeSymbolColorMode()).isEqualTo("player-color");
        assertThat(changed.fullRerender()).isTrue();
    }

    @Test
    @DisplayName("update Toggle flips the setting at once and keeps what is typed in the fields")
    void update_toggle_flipsAndPersistsAtOnce() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        MenuResult click = new MenuResult("action:toggle:leaderboard");
        click.values.put("field_nickname", "TypedButNotSaved");
        UpdateResult<PlayerSettingsUiController.SettingsModel> leaderboard = controller.update(model,
                new PlayerSettingsUiController.SettingsEvent.Toggle(PlayerSettingsUiController.Setting.LEADERBOARD, click), null);
        verify(profileSettings).updateLeaderboard(eq(session.data), eq(false));
        assertThat(leaderboard.model().leaderboard()).isFalse();
        assertThat(leaderboard.model().customNickname()).isEqualTo("TypedButNotSaved");
        assertThat(leaderboard.fullRerender()).isTrue();
        verify(profileSettings, never()).updateCustomNickname(any(), any(), anyBoolean(), anyBoolean());

        UpdateResult<PlayerSettingsUiController.SettingsModel> global = controller.update(model,
                new PlayerSettingsUiController.SettingsEvent.Toggle(PlayerSettingsUiController.Setting.GLOBAL_CHAT,
                        new MenuResult("action:toggle:global_chat")), null);
        verify(profileSettings).updateGlobalChatVisible(eq(session.data), eq(false));
        assertThat(global.model().globalChatVisible()).isFalse();

        UpdateResult<PlayerSettingsUiController.SettingsModel> discord = controller.update(model,
                new PlayerSettingsUiController.SettingsEvent.Toggle(PlayerSettingsUiController.Setting.DISCORD_RELAY,
                        new MenuResult("action:toggle:discord_relay")), null);
        verify(profileSettings).updateDiscordRelayVisible(eq(session.data), eq(true));
        assertThat(discord.model().discordRelayVisible()).isTrue();
    }

    @Test
    @DisplayName("update on Save persists the text fields via service")
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

        UpdateResult<PlayerSettingsUiController.SettingsModel> updateResult = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.Save(result), null
        );

        verify(profileSettings).updateCustomNickname(eq(session.data), eq("NewNick"), eq(true), eq(true));
        verify(profileSettings).updateDescription(eq(session.data), eq("New Description"));
        // The switches take effect when pressed; saving the fields does not touch them.
        verify(profileSettings, never()).updateLeaderboard(any(), anyBoolean());

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

        MenuResult tabChatRes = new MenuResult("action:tab:chat");
        assertThat(controller.parseEvent(tabChatRes)).isEqualTo(
                new PlayerSettingsUiController.SettingsEvent.SelectTab(PlayerSettingsUiController.Tab.CHAT, tabChatRes));

        MenuResult toggleRes = new MenuResult("action:toggle:discord_relay");
        assertThat(controller.parseEvent(toggleRes)).isEqualTo(
                new PlayerSettingsUiController.SettingsEvent.Toggle(PlayerSettingsUiController.Setting.DISCORD_RELAY, toggleRes));

        assertThat(controller.parseEvent(new MenuResult("action:symbol_color:player-color"))).isEqualTo(
                new PlayerSettingsUiController.SettingsEvent.SelectSymbolColorMode("player-color"));
        assertThat(controller.parseEvent(new MenuResult("action:symbol_color:rainbow"))).isNull();

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

    @Test
    @DisplayName("the username is a field while it can be changed and a line of text once it is settled")
    void window_showsTheUsernameAsItCanBeChanged() {
        Session session = createTestSession("uuid-1", false);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, null, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        String settled = LayoutAssert.dsl(controller.window(model.withUsername("steve"), Screen.NARROW));
        assertThat(settled).doesNotContain("field_username");

        String open = LayoutAssert.dsl(controller.window(model.withCanChangeUsername(true), Screen.NARROW));
        assertThat(open).contains("field_username");
        for (IdentityDisplayMode mode : IdentityDisplayMode.values()) {
            assertThat(open).contains("action:identity_mode:" + mode.name().toLowerCase(Locale.ROOT));
        }
    }

    @Test
    @DisplayName("Save takes a new username once, and the field is closed after it")
    void update_save_takesANewUsername() {
        Session session = createTestSession("uuid-1", false);
        session.data.canChangeUsername = true;
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        when(profileSettings.validateCustomNickname(anyString()))
                .thenReturn(PlayerProfileSettingsService.NicknameValidationResult.ok());
        when(profileSettings.validateUsername("steve"))
                .thenReturn(PlayerProfileSettingsService.UsernameValidationResult.ok());
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        MenuResult save = new MenuResult("action:save");
        save.values.put("field_username", " steve ");
        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.Save(save), null);

        verify(profileSettings).updateUsername(session.data, "steve");
        assertThat(result.model().username()).isEqualTo("steve");
        assertThat(result.model().usernameEditable()).isFalse();
        assertThat(result.model().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("Save refuses a username that is not valid or belongs to another player, and saves nothing")
    void update_save_refusesABadUsername() {
        Session session = createTestSession("uuid-1", false);
        session.data.canChangeUsername = true;
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        when(profileSettings.validateCustomNickname(anyString()))
                .thenReturn(PlayerProfileSettingsService.NicknameValidationResult.ok());
        when(profileSettings.validateUsername("no"))
                .thenReturn(PlayerProfileSettingsService.UsernameValidationResult.error("error-username-length"));
        when(profileSettings.validateUsername("taken"))
                .thenReturn(PlayerProfileSettingsService.UsernameValidationResult.ok());
        when(session.playerDataRepository.findByUsername("taken")).thenReturn(new PlayerData("uuid-2", true));
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        for (String name : List.of("no", "taken")) {
            MenuResult save = new MenuResult("action:save");
            save.values.put("field_username", name);
            UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                    model, new PlayerSettingsUiController.SettingsEvent.Save(save), null);

            assertThat(result.model().isSuccess()).as(name).isFalse();
            assertThat(result.model().usernameDraft()).isEqualTo(name);
            assertThat(result.model().username()).isEmpty();
        }
        verify(profileSettings, never()).updateUsername(any(), anyString());
        verify(profileSettings, never()).updateCustomNickname(any(), anyString(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("Save leaves the username alone for a player who may not change it")
    void update_save_ignoresAUsernameThatIsSettled() {
        Session session = createTestSession("uuid-1", false);
        session.data.username = "steve";
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        when(profileSettings.validateCustomNickname(anyString()))
                .thenReturn(PlayerProfileSettingsService.NicknameValidationResult.ok());
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        MenuResult save = new MenuResult("action:save");
        save.values.put("field_username", "another");
        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(
                model, new PlayerSettingsUiController.SettingsEvent.Save(save), null);

        verify(profileSettings, never()).updateUsername(any(), anyString());
        assertThat(result.model().username()).isEqualTo("steve");
    }

    @Test
    @DisplayName("choosing how the identity is shown persists a change and keeps what is typed")
    void update_selectIdentityDisplayMode_persistsOnlyAChange() {
        Session session = createTestSession("uuid-1", false);
        session.data.identityDisplayMode = IdentityDisplayMode.PID;
        PlayerProfileSettingsService profileSettings = mock(PlayerProfileSettingsService.class);
        PlayerSettingsUiController controller = new PlayerSettingsUiController(null, profileSettings, session, session.data);
        PlayerSettingsUiController.SettingsModel model = PlayerSettingsUiController.createModel(session, session.data);

        MenuResult pressed = new MenuResult("action:identity_mode:both");
        pressed.values.put("field_nickname", "Typed");
        PlayerSettingsUiController.SettingsEvent event = controller.parseEvent(pressed);
        assertThat(event).isEqualTo(
                new PlayerSettingsUiController.SettingsEvent.SelectIdentityDisplayMode(IdentityDisplayMode.BOTH, pressed));

        UpdateResult<PlayerSettingsUiController.SettingsModel> result = controller.update(model, event, null);
        verify(profileSettings).updateIdentityDisplayMode(session.data, IdentityDisplayMode.BOTH);
        assertThat(result.model().identityDisplayMode()).isEqualTo(IdentityDisplayMode.BOTH);
        assertThat(result.model().customNickname()).isEqualTo("Typed");

        controller.update(result.model(), event, null);
        verify(profileSettings, times(1)).updateIdentityDisplayMode(any(), any());
    }
}
