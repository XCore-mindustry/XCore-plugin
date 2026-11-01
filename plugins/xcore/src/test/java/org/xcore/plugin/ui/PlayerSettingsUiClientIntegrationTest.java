package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.core.GameState;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.testkit.ui.DeterministicUiLoop;

import java.util.Locale;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration test driving MenuService, UiSession, and PlayerSettingsUiController
 * through DeterministicUiLoop and HeadlessMenuClient to verify the delivered layout, tab
 * switches, saving, and dismissal.
 */
class PlayerSettingsUiClientIntegrationTest {

    private GameState originalState;
    private DeterministicUiLoop loop;
    private DeterministicUiLoop.CoreAppScope coreApp;
    private MenuService menuService;
    private PlayerProfileSettingsService profileSettings;
    private Session session;
    private SessionService sessionService;
    private PlayerMenu playerMenu;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        originalState = state;
        state = new GameState();

        loop = new DeterministicUiLoop();

        coreApp = loop.installAsCoreApp();

        profileSettings = mock(PlayerProfileSettingsService.class);
        when(profileSettings.validateCustomNickname(anyString()))
                .thenReturn(PlayerProfileSettingsService.NicknameValidationResult.ok());

        PlayerData data = new PlayerData("test-player-uuid", true);
        data.nickname = "MobileUser";
        data.customNickname = "Speedy";
        data.description = "Mobile player on landscape screen";
        data.globalChatVisible = true;
        data.discordRelayVisible = false;
        data.leaderboard = true;

        Bundle bundle = mock(Bundle.class);
        com.ospx.flubundle.Localizer localizer = mock(com.ospx.flubundle.Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        sessionService = mock(SessionService.class);
        Provider<SessionService> sessionProvider = () -> sessionService;

        MindustryMenuGateway gateway = new LoopMenuGateway(loop);

        menuService = new MenuService(sessionProvider, gateway);
        menuService.init();

        Player player = Player.create();
        player.con = mock(NetConnection.class);

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );
        when(sessionService.get(anyString())).thenReturn(session);

        loop.onChoose(choose -> menuService.onMenuBuilderResult(session, choose.toMenuResult()));

        playerMenu = new PlayerMenu(
                new TomlSecretsConfig(),
                sessionService,
                null,
                null,
                bundle,
                mock(PlayerDisplayService.class),
                profileSettings,
                null,
                menuService,
                null,
                null
        );
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        coreApp.close();
    }

    @Test
    @DisplayName("Open settings on client delivers a scrollable pane for every screen class")
    void openSettings_deliversScrollablePaneForEveryScreenClass() {
        playerMenu.openSettingsUi(session, session.data);
        int menuId = menuService.getMenuBuilderId();

        assertThat(loop.client().isVisible(menuId)).isFalse();

        // Step server -> client
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();
        assertThat(session.hasActiveUiSession()).isTrue();

        // Inspect delivered binary wire DSL
        var lastMsg = loop.transcript().lastShow();
        String dsl = lastMsg.dsl();

        // Verify mobile responsiveness: a pane of cards per screen class, picked by the client
        assertThat(dsl).contains("pane{");
        assertThat(dsl).contains("condition: \"width < 490\"");
        assertThat(dsl).contains("condition: \"width >= 800\"");

        assertThat(dsl).contains("action:save");
        assertThat(dsl).contains("action:tab:profile");
        assertThat(dsl).contains("action:tab:badges");
    }

    @Test
    @DisplayName("Clicking tab switch button rerenders dialog with selected tab content")
    void switchTab_rerendersWithNewTabContent() {
        playerMenu.openSettingsUi(session, session.data);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client clicks Badges tab
        loop.client().click(menuId, "action:tab:badges");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server processes event and emits rerender
        assertThat(loop.stepServerToClient()).isTrue();
        var lastMsg = loop.transcript().lastShow();
        String dsl = lastMsg.dsl();

        assertThat(dsl).contains("action:badges_filter:my");
        assertThat(dsl).contains("action:symbol_color:player-color");

        // Dialog remains visible on client
        assertThat(loop.client().isVisible(menuId)).isTrue();
    }

    @Test
    @DisplayName("Submitting settings persists form data and sends rerendered feedback update")
    void submitSettings_persistsAndSendsFeedbackUpdate() {
        playerMenu.openSettingsUi(session, session.data);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client submits form with values
        var result = new MenuResult("action:save");
        result.values.put("field_nickname", "NewMobileNick");
        result.values.put("field_description", "Updated bio");

        menuService.onMenuBuilderResult(session, result);
        loop.serverPost().runTurn();

        // Step server -> client: show/rerender emitted
        assertThat(loop.stepServerToClient()).isTrue();
        var lastMsg = loop.transcript().lastShow();
        String dsl = lastMsg.dsl();
        assertThat(dsl).contains("player-settings-saved");

        // Verify profile service received form updates
        verify(profileSettings).updateCustomNickname(eq(session.data), eq("NewMobileNick"), eq(true), eq(true));
        verify(profileSettings).updateDescription(eq(session.data), eq("Updated bio"));
    }

    @Test
    @DisplayName("Android back button / Escape dismissal closes session and cleans up server state")
    void dismissDialog_closesSessionCleanly() {
        playerMenu.openSettingsUi(session, session.data);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();

        // Client presses Android back button or clicks outside dialog (null action)
        loop.client().dismiss(menuId);
        assertThat(loop.stepClientToServer()).isTrue();

        // Server receives cancelled result and closes UI session
        assertThat(session.hasActiveUiSession()).isFalse();
    }
}
