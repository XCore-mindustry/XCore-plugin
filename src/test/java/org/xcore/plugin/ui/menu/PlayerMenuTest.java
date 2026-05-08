package org.xcore.plugin.ui;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MindustryMenuGateway;
import org.xcore.plugin.ui.menu.AuditHistoryMenu;
import org.xcore.plugin.ui.menu.PlayerMenu;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerMenuTest {

    private SessionService sessionService;
    private GameDataRepository gameDataRepository;
    private PlayerDisplayService playerDisplayService;
    private PlayerProfileSettingsService profileSettings;
    private AuditHistoryMenu auditHistoryMenu;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private PlayerMenu playerMenu;
    private Session session;
    private PlayerData targetData;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        gameDataRepository = mock(GameDataRepository.class);
        playerDisplayService = mock(PlayerDisplayService.class);
        profileSettings = mock(PlayerProfileSettingsService.class);
        auditHistoryMenu = mock(AuditHistoryMenu.class);
        gateway = mock(MindustryMenuGateway.class);

        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        Config config = new Config();
        config.server = "mini-pvp";
        GlobalConfig globalConfig = new GlobalConfig();

        playerMenu = new PlayerMenu(
                config, globalConfig, sessionService,
                gameDataRepository,
                mock(Bundle.class),
                playerDisplayService, profileSettings,
                auditHistoryMenu);

        session = session();
        targetData = session.data;

        when(sessionService.get("viewer-1")).thenReturn(session);
        when(gameDataRepository.aggregatePlayerStatsOverview(anyString()))
                .thenReturn(new PlayerStatsOverview(
                        AggregatedPlayerStats.EMPTY,
                        ModeStatsSummary.EMPTY,
                        ModeStatsSummary.EMPTY,
                        ModeStatsSummary.EMPTY));
        when(profileSettings.validateCustomNickname(anyString()))
                .thenReturn(PlayerProfileSettingsService.NicknameValidationResult.ok());
    }

    @Test
    @DisplayName("custom nickname button opens active prompt and leaves textHandler null")
    void customNicknameButton_opensActivePromptAndLeavesTextHandlerNull() {
        playerMenu.settings("viewer-1", targetData);
        menuService.onMenuOption(session, 0);

        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.textHandler).isNull();
        verify(gateway).textInput(eq(session.player), eq(0),
                eq("player-menu-settings-customNickname-title"),
                eq("player-menu-settings-customNickname-message"),
                eq(256), eq(targetData.customNickname), eq(false));
    }

    @Test
    @DisplayName("custom nickname prompt submit validates and updates via service")
    void customNicknamePromptSubmit_validatesAndUpdatesViaService() {
        playerMenu.settings("viewer-1", targetData);
        menuService.onMenuOption(session, 0);

        menuService.onTextInput(session, "new nick");

        verify(profileSettings).validateCustomNickname("new nick");
        verify(profileSettings).updateCustomNickname(targetData, "new nick", true, true);
        assertThat(session.activePrompt()).isNull();
    }

    @Test
    @DisplayName("custom nickname prompt cancel returns to settings without updating")
    void customNicknamePromptCancel_returnsWithoutUpdating() {
        playerMenu.settings("viewer-1", targetData);
        menuService.onMenuOption(session, 0);

        menuService.onTextInput(session, null);

        verify(profileSettings, never()).updateCustomNickname(any(), anyString(), anyBoolean(), anyBoolean());
        assertThat(session.activePrompt()).isNull();
    }

    @Test
    @DisplayName("custom nickname prompt rejects invalid nickname and returns to settings")
    void customNicknamePromptSubmit_invalidNickname_returnsWithoutUpdating() {
        when(profileSettings.validateCustomNickname("bad"))
                .thenReturn(PlayerProfileSettingsService.NicknameValidationResult.tooLong(40));

        playerMenu.settings("viewer-1", targetData);
        menuService.onMenuOption(session, 0);

        menuService.onTextInput(session, "bad");

        verify(profileSettings, never()).updateCustomNickname(any(), anyString(), anyBoolean(), anyBoolean());
        assertThat(session.activePrompt()).isNull();
    }

    @Test
    @DisplayName("description button opens active prompt and leaves textHandler null")
    void descriptionButton_opensActivePromptAndLeavesTextHandlerNull() {
        playerMenu.settings("viewer-1", targetData);
        menuService.onMenuOption(session, 2);

        assertThat(session.activePrompt()).isNotNull();
        assertThat(session.textHandler).isNull();
        verify(gateway).textInput(eq(session.player), eq(0),
                eq("player-menu-settings-description-title"),
                eq(""),
                eq(1000), eq(targetData.description), eq(false));
    }

    @Test
    @DisplayName("description prompt submit updates via service")
    void descriptionPromptSubmit_updatesViaService() {
        playerMenu.settings("viewer-1", targetData);
        menuService.onMenuOption(session, 2);

        menuService.onTextInput(session, "new description");

        verify(profileSettings).updateDescription(targetData, "new description");
        assertThat(session.activePrompt()).isNull();
    }

    @Test
    @DisplayName("description prompt cancel returns without updating")
    void descriptionPromptCancel_returnsWithoutUpdating() {
        playerMenu.settings("viewer-1", targetData);
        menuService.onMenuOption(session, 2);

        menuService.onTextInput(session, null);

        verify(profileSettings, never()).updateDescription(any(), anyString());
        assertThat(session.activePrompt()).isNull();
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("viewer-1", true);
        data.uuid = "viewer-1";
        data.pid = 7;
        data.customNickname = "old nick";
        data.description = "old desc";
        data.language = "auto";
        data.translatorLanguage = "off";
        data.globalChatVisible = true;
        data.discordRelayVisible = true;
        data.leaderboard = true;
        data.activeBadge = "";
        data.badgeSymbolColorMode = "default";

        Session session = new Session(
                new GlobalConfig(),
                mock(Bundle.class),
                menuService,
                mock(PlayerDataRepository.class),
                player,
                data
        );

        Localization localization = mock(Localization.class);
        when(localization.t(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.t(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.format(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));
        when(localization.getLocale()).thenReturn(Locale.US);
        when(localization.getLanguageName(anyString(), anyString())).thenReturn("English");
        session.localization = localization;

        return session;
    }
}
