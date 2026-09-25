package org.xcore.plugin.ui;

import arc.Core;
import arc.mock.MockApplication;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.core.GameState;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder.NodeBuilder;
import mindustry.ui.builder.UiDslWriter;
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
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.testkit.ui.UiWireMessage;

import java.util.Locale;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration test driving MenuService, UiSession, and PlayerSettingsUiController
 * through DeterministicUiLoop and HeadlessMenuClient to verify mobile landscape scrolling,
 * responsive layout constraints, partial slot updates, and cancel/close handling.
 */
class PlayerSettingsUiClientIntegrationTest {

    private GameState originalState;
    private DeterministicUiLoop loop;
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

        Core.app = new MockApplication() {
            @Override
            public void post(Runnable runnable) {
                loop.serverPost().post(runnable);
            }
        };

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

        MindustryMenuGateway gateway = new MindustryMenuGateway() {
            @Override public void menu(Player p, int id, String t, String c, String[][] b) {}
            @Override public void followUpMenu(Player p, int id, String t, String c, String[][] b) {}
            @Override public void hideFollowUpMenu(Player p, int id) {}
            @Override public void textInput(Player p, int id, String t, String c, int l, String d, boolean n) {}
            @Override public void openUri(Player p, String u) {}
            @Override public void copyToClipboard(Player p, String t) {}

            @Override
            public void menuBuilder(Player player, int menuId, long token, String title,
                                    boolean hideOnClick, boolean hideExisting, boolean fillScreen, NodeBuilder<?> ui) {
                loop.sendServerToClient(new UiWireMessage.Show(menuId, token, hideExisting, UiSnapshot.capture(ui)));
            }

            @Override
            public void menuBuilderUpdate(Player player, int menuId, String tableId, NodeBuilder<?> ui) {
                loop.sendServerToClient(new UiWireMessage.Update(menuId, tableId, UiSnapshot.capture(ui)));
            }

            @Override
            public void hideMenuBuilder(Player player, int menuId) {
                loop.sendServerToClient(new UiWireMessage.Hide(menuId));
            }
        };

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

        loop.onClientMessage(msg -> {
            if (msg instanceof UiWireMessage.Choose choose) {
                var result = new MenuResult(choose.action());
                result.token = choose.token();
                menuService.onMenuBuilderResult(session, result);
            }
        });

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
                null
        );
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        Core.app = null;
    }

    @Test
    @DisplayName("Open settings on client delivers scrollable pane with max height and close buttons")
    void openSettings_deliversScrollablePaneWithCloseButtons() {
        playerMenu.openSettingsUi(session, session.data);
        int menuId = menuService.getMenuBuilderId();

        assertThat(loop.client().isVisible(menuId)).isFalse();

        // Step server -> client
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();
        assertThat(session.hasActiveUiSession()).isTrue();

        // Inspect delivered binary wire DSL
        var lastMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();
        String dsl = UiDslWriter.write((NodeBuilder<?>) lastMsg.body().decode());

        // Verify mobile responsiveness: ScrollPane exists with constrained maxHeight
        assertThat(dsl).contains("pane{");
        assertThat(dsl).contains("maxHeight: 280");

        // Verify top-right quick close button and footer cancel button both exist
        assertThat(dsl).contains("action:close");
        assertThat(dsl).contains("action:save");
    }

    @Test
    @DisplayName("Clicking language toggle emits partial Update message inside the scroll pane without redrawing whole dialog")
    void toggleLanguage_emitsPartialSlotUpdate() {
        playerMenu.openSettingsUi(session, session.data);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client clicks toggle language
        loop.client().click(menuId, "action:toggle_lang");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server processes event and emits partial update for SLOT_LANG
        assertThat(loop.stepServerToClient()).isTrue();
        var lastMsg = (UiWireMessage.Update) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Update)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(lastMsg.targetId()).isEqualTo("slot_lang");

        // Dialog remains visible on client
        assertThat(loop.client().isVisible(menuId)).isTrue();
    }

    @Test
    @DisplayName("Clicking close button closes the settings dialog cleanly")
    void clickClose_hidesDialogAndClosesSession() {
        playerMenu.openSettingsUi(session, session.data);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();

        // Client clicks top close (✕) or bottom Cancel button
        loop.client().click(menuId, "action:close");
        assertThat(loop.stepClientToServer()).isTrue();

        // Server emits Hide message to client
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isFalse();
        assertThat(session.hasActiveUiSession()).isFalse();
    }

    @Test
    @DisplayName("Submitting settings persists form data and sends partial feedback update")
    void submitSettings_persistsAndSendsFeedbackUpdate() {
        playerMenu.openSettingsUi(session, session.data);
        int menuId = menuService.getMenuBuilderId();
        assertThat(loop.stepServerToClient()).isTrue();

        // Client submits form with values
        var result = new MenuResult("action:save");
        result.values.put("field_nickname", "NewMobileNick");
        result.values.put("field_description", "Updated bio");
        result.values.put("check_global_chat", false);
        result.values.put("check_discord_relay", true);
        result.values.put("check_leaderboard", false);

        menuService.onMenuBuilderResult(session, result);
        loop.serverPost().runTurn();

        // Step server -> client: feedback update emitted
        assertThat(loop.stepServerToClient()).isTrue();
        var lastMsg = (UiWireMessage.Update) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Update)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(lastMsg.targetId()).isEqualTo("slot_feedback");

        // Verify profile service received form updates
        verify(profileSettings).updateCustomNickname(eq(session.data), eq("NewMobileNick"), eq(true), eq(true));
        verify(profileSettings).updateDescription(eq(session.data), eq("Updated bio"));
        verify(profileSettings).updateGlobalChatVisible(eq(session.data), eq(false));
        verify(profileSettings).updateDiscordRelayVisible(eq(session.data), eq(true));
        verify(profileSettings).updateLeaderboard(eq(session.data), eq(false));
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
