package org.xcore.plugin.ui;

import arc.struct.Seq;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.model.Slice;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.service.PlayerProfileSettingsService;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MindustryMenuGateway;
import org.xcore.plugin.ui.menu.AuditHistoryMenu;
import org.xcore.plugin.ui.menu.PlayerMenu;

import java.util.Locale;
import java.util.stream.Stream;

import org.xcore.plugin.common.StatusEnum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerMenuTest {

    private SessionService sessionService;
    private GameDataRepository gameDataRepository;
    private PlayerDisplayService playerDisplayService;
    private PlayerProfileSettingsService profileSettings;
    private AuditService auditService;
    private AuditHistoryMenu auditHistoryMenu;
    private MindustryMenuGateway gateway;
    private MenuService menuService;
    private PlayerMenu playerMenu;
    private Session session;
    private PlayerData targetData;
    private PlayerDataRepository playerDataRepository;
    private Bundle bundle;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        sessionService = mock(SessionService.class);
        gameDataRepository = mock(GameDataRepository.class);
        playerDisplayService = mock(PlayerDisplayService.class);
        profileSettings = mock(PlayerProfileSettingsService.class);
        auditService = mock(AuditService.class);
        gateway = mock(MindustryMenuGateway.class);

        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        menuService = new MenuService(sessionProvider, gateway);

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.eventsPerPage = 2;

        bundle = mock(Bundle.class);
        when(bundle.getAvailableLocales()).thenReturn(new Seq<>());

        playerDataRepository = mock(PlayerDataRepository.class);
        auditHistoryMenu = new AuditHistoryMenu(globalConfig, sessionService, auditService, menuService);
        auditHistoryMenu.init();
        when(playerDisplayService.resolveBaseName(any(), any())).thenAnswer(invocation -> {
            PlayerData data = invocation.getArgument(0);
            return data.nickname;
        });

        playerMenu = new PlayerMenu(
                globalConfig, sessionService,
                gameDataRepository,
                bundle,
                playerDisplayService, profileSettings,
                auditHistoryMenu,
                menuService);
        playerMenu.init();

        session = session();
        targetData = session.data;
        when(playerDataRepository.findByUuid(targetData.uuid)).thenReturn(targetData);

        when(sessionService.get("viewer-1")).thenReturn(session);
        when(gameDataRepository.aggregatePlayerStatsOverview(anyString()))
                .thenReturn(new PlayerStatsOverview(
                        AggregatedPlayerStats.EMPTY,
                        ModeStatsSummary.EMPTY,
                        ModeStatsSummary.EMPTY,
                        ModeStatsSummary.EMPTY));
        when(auditService.findSummaryByTargetUuid(anyString(), any(), anyInt()))
                .thenReturn(new Slice<AuditRecordSummary>(java.util.List.of(), false, null));
        when(profileSettings.validateCustomNickname(anyString()))
                .thenReturn(PlayerProfileSettingsService.NicknameValidationResult.ok());
    }

    @Test
    @DisplayName("player renders routed screen with route metadata")
    void player_rendersRoutedScreenWithRouteMetadata() {
        playerMenu.player("viewer-1", targetData);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.profile");
        verify(gateway).menu(eq(session.player), eq(0), eq("player-menu-player-title"), any(), any());
    }

    @Test
    @DisplayName("player settings navigation opens routed settings via route history")
    void player_settingsNavigation_opensRoutedSettingsViaRouteHistory() {
        playerMenu.player("viewer-1", targetData);

        menuService.onMenuOption(session, 0);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.settings");
    }

    @Test
    @DisplayName("player players navigation opens routed players via route history")
    void player_playersNavigation_opensRoutedPlayersViaRouteHistory() {
        playerMenu.player("viewer-1", targetData);

        menuService.onMenuOption(session, 1);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.players");
    }

    @Test
    @DisplayName("player admin audit navigation opens routed audit history via route history")
    void player_adminAuditNavigation_opensRoutedAuditHistoryViaRouteHistory() {
        session.player.admin = true;
        targetData.admin = true;

        playerMenu.player("viewer-1", targetData);

        menuService.onMenuOption(session, 1);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("audit.history");
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
    @DisplayName("custom nickname reset button clears nickname without opening prompt")
    void customNicknameResetButton_clearsNicknameWithoutOpeningPrompt() {
        playerMenu.settings("viewer-1", targetData);

        menuService.onMenuOption(session, 1);

        verify(profileSettings).updateCustomNickname(targetData, "", true, true);
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

    @Test
    @DisplayName("settings renders routed screen with route metadata")
    void settings_rendersRoutedScreenWithRouteMetadata() {
        playerMenu.settings("viewer-1", targetData);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.settings");
        verify(gateway).menu(eq(session.player), eq(0), eq("player-menu-settings-title"), any(), any());
    }

    @Test
    @DisplayName("settings leaderboard toggle updates service and re-renders")
    void settings_leaderboardToggle_updatesServiceAndRerenders() {
        playerMenu.settings("viewer-1", targetData);

        menuService.onMenuOption(session, 5);

        verify(profileSettings).updateLeaderboard(targetData, false);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("player-menu-settings-title"), any(), any());
    }

    @Test
    @DisplayName("settings chat settings navigation opens routed child via route history")
    void settings_chatSettingsNavigation_opensRoutedChildViaRouteHistory() {
        playerMenu.settings("viewer-1", targetData);

        menuService.onMenuOption(session, 3);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.chat-settings");
    }

    @Test
    @DisplayName("settings badges navigation opens routed child via route history")
    void settings_badgesNavigation_opensRoutedChildViaRouteHistory() {
        playerMenu.settings("viewer-1", targetData);

        menuService.onMenuOption(session, 4);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.badges");
    }

    @Test
    @DisplayName("settings language selection opens routed language selection")
    void settings_languageSelection_opensRoutedLanguageSelection() {
        playerMenu.settings("viewer-1", targetData);

        menuService.onMenuOption(session, 6);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.language-selection");
        verify(gateway).menu(eq(session.player), eq(0), eq("player-menu-settings-language-title"), any(), any());
    }

    @Test
    @DisplayName("settings denies access for non-admin viewing another player")
    void settings_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.settings("viewer-1", otherData);

        verify(gateway, never()).menu(any(), anyInt(), anyString(), anyString(), any());
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("chatSettings renders routed screen with route metadata")
    void chatSettings_rendersRoutedScreenWithRouteMetadata() {
        playerMenu.chatSettings("viewer-1", targetData);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.chat-settings");
        verify(gateway).menu(eq(session.player), eq(0), eq("player-menu-settings-chat-title"), any(), any());
    }

    @Test
    @DisplayName("chatSettings toggle global chat updates service and re-renders")
    void chatSettings_toggleGlobalChat_updatesServiceAndRerenders() {
        playerMenu.chatSettings("viewer-1", targetData);

        menuService.onMenuOption(session, 0);

        verify(profileSettings).updateGlobalChatVisible(targetData, false);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("player-menu-settings-chat-title"), any(), any());
    }

    @Test
    @DisplayName("chatSettings toggle discord relay updates service and re-renders")
    void chatSettings_toggleDiscordRelay_updatesServiceAndRerenders() {
        playerMenu.chatSettings("viewer-1", targetData);

        menuService.onMenuOption(session, 1);

        verify(profileSettings).updateDiscordRelayVisible(targetData, false);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("player-menu-settings-chat-title"), any(), any());
    }

    @Test
    @DisplayName("chatSettings translator language action opens routed language selection")
    void chatSettings_translatorLanguage_opensRoutedLanguageSelection() {
        playerMenu.chatSettings("viewer-1", targetData);

        menuService.onMenuOption(session, 2);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.language-selection");
        verify(gateway).menu(eq(session.player), eq(0), eq("player-menu-settings-translator-title"), any(), any());
    }

    @Test
    @DisplayName("language selection renders routed screen for interface language")
    void languageSelection_interfaceLanguage_rendersRoutedScreen() {
        playerMenu.languageSelectionMenu("viewer-1", targetData, false);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.language-selection");
        verify(gateway).menu(eq(session.player), eq(0), eq("player-menu-settings-language-title"), any(), any());
    }

    @Test
    @DisplayName("language selection renders routed screen for translator language")
    void languageSelection_translatorLanguage_rendersRoutedScreen() {
        playerMenu.languageSelectionMenu("viewer-1", targetData, true);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.language-selection");
        verify(gateway).menu(eq(session.player), eq(0), eq("player-menu-settings-translator-title"), any(), any());
    }

    @Test
    @DisplayName("language selection auto option updates language and returns to settings")
    void languageSelection_auto_updatesLanguageAndReturnsToSettings() {
        playerMenu.settings("viewer-1", targetData);

        menuService.onMenuOption(session, 6); // open language selection from settings

        menuService.onMenuOption(session, 0); // select auto

        verify(profileSettings).updateLanguage(targetData, "auto");
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.settings");
    }

    @Test
    @DisplayName("language selection default option updates translator language and returns to chat settings")
    void languageSelection_default_updatesTranslatorLanguageAndReturnsToChatSettings() {
        playerMenu.chatSettings("viewer-1", targetData);

        menuService.onMenuOption(session, 2); // open language selection from chat settings

        menuService.onMenuOption(session, 0); // select default

        verify(profileSettings).updateTranslatorLanguage(targetData, "off");
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.chat-settings");
    }

    @Test
    @DisplayName("language selection locale option updates language and returns to parent")
    void languageSelection_locale_updatesLanguageAndReturnsToParent() {
        when(bundle.getAvailableLocales()).thenReturn(Seq.with(Locale.ENGLISH));

        playerMenu.settings("viewer-1", targetData);

        menuService.onMenuOption(session, 6); // open language selection from settings
        // Row 0: auto
        // Row 1: English
        // Row 2: back, close
        menuService.onMenuOption(session, 1); // select English

        verify(profileSettings).updateLanguage(targetData, "en");
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.settings");
    }

    @Test
    @DisplayName("language selection locale rows use namespaced action ids")
    void languageSelection_localeRows_useNamespacedActionIds() {
        when(bundle.getAvailableLocales()).thenReturn(Seq.with(Locale.ENGLISH, new Locale("uk")));

        playerMenu.languageSelectionMenu("viewer-1", targetData, false);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().actionIdAt(0)).isEqualTo("auto");
        assertThat(session.activeScreen().actionIdAt(1)).isEqualTo("lang:en");
        assertThat(session.activeScreen().actionIdAt(2)).isEqualTo("lang:uk_UA");
    }

    @Test
    @DisplayName("language selection maps uk locale to uk_UA code")
    void languageSelection_ukLocale_mapsToUkUa() {
        Locale ukLocale = new Locale("uk");
        when(bundle.getAvailableLocales()).thenReturn(Seq.with(ukLocale));

        playerMenu.settings("viewer-1", targetData);

        menuService.onMenuOption(session, 6); // open language selection from settings
        menuService.onMenuOption(session, 1); // select Ukrainian

        verify(profileSettings).updateLanguage(targetData, "uk_UA");
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.settings");
    }

    @Test
    @DisplayName("chatSettings denies access for non-admin viewing another player")
    void chatSettings_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.chatSettings("viewer-1", otherData);

        verify(gateway, never()).menu(any(), anyInt(), anyString(), anyString(), any());
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("badgeSymbolColorMode renders routed screen with route metadata")
    void badgeSymbolColorMode_rendersRoutedScreenWithRouteMetadata() {
        playerMenu.badgeSymbolColorMode("viewer-1", targetData);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.badge-symbol-color");
        verify(gateway).menu(eq(session.player), eq(0), eq("badge-menu-symbol-color-title"), any(), any());
    }

    @Test
    @DisplayName("badgeSymbolColorMode selecting default mode updates service and re-renders")
    void badgeSymbolColorMode_selectDefaultMode_updatesServiceAndRerenders() {
        playerMenu.badgeSymbolColorMode("viewer-1", targetData);

        menuService.onMenuOption(session, 0);

        verify(profileSettings).updateBadgeSymbolColorMode(targetData, "default", true, true);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("badge-menu-symbol-color-title"), any(), any());
    }

    @Test
    @DisplayName("badgeSymbolColorMode selecting player-color mode updates service and re-renders")
    void badgeSymbolColorMode_selectPlayerColorMode_updatesServiceAndRerenders() {
        playerMenu.badgeSymbolColorMode("viewer-1", targetData);

        menuService.onMenuOption(session, 1);

        verify(profileSettings).updateBadgeSymbolColorMode(targetData, "player-color", true, true);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("badge-menu-symbol-color-title"), any(), any());
    }

    @Test
    @DisplayName("badgeSymbolColorMode denies access for non-admin viewing another player")
    void badgeSymbolColorMode_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.badgeSymbolColorMode("viewer-1", otherData);

        verify(gateway, never()).menu(any(), anyInt(), anyString(), anyString(), any());
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("badges renders routed screen with route metadata")
    void badges_rendersRoutedScreenWithRouteMetadata() {
        playerMenu.badges("viewer-1", targetData);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.badges");
        verify(gateway).menu(eq(session.player), eq(0), eq("badge-menu-title"), any(), any());
    }

    @Test
    @DisplayName("badges selecting unlocked badge updates service and re-renders")
    void badges_selectingUnlockedBadge_updatesServiceAndRerenders() {
        targetData.unlockedBadges.add("developer");

        playerMenu.badges("viewer-1", targetData);

        menuService.onMenuOption(session, 0);

        verify(profileSettings).updateActiveBadge(targetData, "developer", true, true);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("badge-menu-title"), any(), any());
    }

    @Test
    @DisplayName("badges rows use namespaced action ids")
    void badges_rowsUseNamespacedActionIds() {
        targetData.unlockedBadges.add("developer");

        playerMenu.badges("viewer-1", targetData);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().actionIdAt(0)).isEqualTo("badge:developer");
    }

    @Test
    @DisplayName("badges symbol color button opens routed symbol color mode via route history")
    void badges_symbolColorButton_opensRoutedSymbolColorModeViaRouteHistory() {
        playerMenu.badges("viewer-1", targetData);

        menuService.onMenuOption(session, 0);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.badge-symbol-color");
    }

    @Test
    @DisplayName("badges view all button opens routed all badges via route history")
    void badges_viewAllButton_opensRoutedAllBadgesViaRouteHistory() {
        playerMenu.badges("viewer-1", targetData);

        menuService.onMenuOption(session, 1);

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.all-badges");
    }

    @Test
    @DisplayName("badges clear button clears active badge and re-renders")
    void badges_clearButton_clearsActiveBadgeAndRerenders() {
        targetData.activeBadge = "developer";

        playerMenu.badges("viewer-1", targetData);

        menuService.onMenuOption(session, 2);

        verify(profileSettings).updateActiveBadge(targetData, "", true, true);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("badge-menu-title"), any(), any());
    }

    @Test
    @DisplayName("badges denies access for non-admin viewing another player")
    void badges_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.badges("viewer-1", otherData);

        verify(gateway, never()).menu(any(), anyInt(), anyString(), anyString(), any());
        assertThat(session.activeScreen()).isNull();
    }

    @Test
    @DisplayName("allBadges renders routed screen with route metadata")
    void allBadges_rendersRoutedScreenWithRouteMetadata() {
        playerMenu.allBadges("viewer-1", targetData);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.all-badges");
        verify(gateway).menu(eq(session.player), eq(0), eq("badge-menu-all-title"), any(), any());
    }

    @Test
    @DisplayName("allBadges selecting owned selectable badge updates service and re-renders")
    void allBadges_selectingOwnedSelectableBadge_updatesServiceAndRerenders() {
        targetData.unlockedBadges.add("developer");

        playerMenu.allBadges("viewer-1", targetData);

        menuService.onMenuOption(session, 1); // DEVELOPER

        verify(profileSettings).updateActiveBadge(targetData, "developer", true, true);
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("badge-menu-all-title"), any(), any());
    }

    @Test
    @DisplayName("all badges rows use namespaced action ids")
    void allBadges_rowsUseNamespacedActionIds() {
        playerMenu.allBadges("viewer-1", targetData);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().actionIdAt(0)).isEqualTo("badge:admin");
        assertThat(session.activeScreen().actionIdAt(1)).isEqualTo("badge:developer");
    }

    @Test
    @DisplayName("allBadges selecting locked or system badge does not update service and re-renders")
    void allBadges_selectingLockedOrSystemBadge_doesNotUpdateServiceAndRerenders() {
        playerMenu.allBadges("viewer-1", targetData);

        menuService.onMenuOption(session, 0); // ADMIN (system)

        verify(profileSettings, never()).updateActiveBadge(any(), anyString(), anyBoolean(), anyBoolean());
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("badge-menu-all-title"), any(), any());
    }

    @Test
    @DisplayName("allBadges denies access for non-admin viewing another player")
    void allBadges_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.allBadges("viewer-1", otherData);

        verify(gateway, never()).menu(any(), anyInt(), anyString(), anyString(), any());
        assertThat(session.activeScreen()).isNull();
    }

    private Session session() {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        PlayerData data = new PlayerData("viewer-1", true);
        data.uuid = "viewer-1";
        data.pid = 7;
        data.nickname = "viewer";
        data.customNickname = "old nick";
        data.description = "old desc";
        data.language = "auto";
        data.translatorLanguage = "off";
        data.globalChatVisible = true;
        data.discordRelayVisible = true;
        data.leaderboard = true;
        data.activeBadge = "";
        data.badgeSymbolColorMode = "default";

        when(playerDataRepository.findByUuid("viewer-1")).thenReturn(data);

        Session session = new Session(
                new GlobalConfig(),
                mock(Bundle.class),
                menuService,
                playerDataRepository,
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

    private Session createOnlineSession(String uuid, String nickname, int pid, boolean admin) {
        Player player = Player.create();
        player.con = mock(NetConnection.class);
        player.admin = admin;
        PlayerData data = new PlayerData(uuid, true);
        data.uuid = uuid;
        data.pid = pid;
        data.nickname = nickname;
        data.admin = admin;
        data.customNickname = "";
        data.description = "";
        data.language = "auto";
        data.translatorLanguage = "off";
        data.globalChatVisible = true;
        data.discordRelayVisible = true;
        data.leaderboard = true;
        data.activeBadge = "";
        data.badgeSymbolColorMode = "default";

        Session s = new Session(
                new GlobalConfig(),
                mock(Bundle.class),
                menuService,
                playerDataRepository,
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
        s.localization = localization;

        return s;
    }

    @Test
    @DisplayName("players renders routed screen with route metadata")
    void players_rendersRoutedScreenWithRouteMetadata() {
        when(sessionService.streamCached()).thenReturn(Stream.of(session));

        playerMenu.players("viewer-1", 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.players");
        verify(gateway).menu(eq(session.player), eq(0), eq("player-menu-players-title"), any(), any());
    }

    @Test
    @DisplayName("players pagination next and previous transitions pages")
    void players_pagination_nextAndPreviousTransitionsPages() {
        Session adminSession = createOnlineSession("admin-1", "admin", 1, true);
        Session player2 = createOnlineSession("player-2", "player2", 2, false);
        when(sessionService.streamCached()).thenAnswer(invocation -> Stream.of(session, adminSession, player2));

        playerMenu.players("viewer-1", 1);

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.players");

        // Page 1 layout: admin-filter(0), next(1), select-0(2), select-1(3), back(4), close(5)
        menuService.onMenuOption(session, 1); // next

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.players");
        verify(gateway, times(2)).menu(eq(session.player), eq(0), eq("player-menu-players-title"), any(), any());

        // Page 2 layout: admin-filter(0), prev(1), select-0(2), back(3), close(4)
        menuService.onMenuOption(session, 1); // prev

        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.players");
        verify(gateway, times(3)).menu(eq(session.player), eq(0), eq("player-menu-players-title"), any(), any());
    }

    @Test
    @DisplayName("players admin filter toggles through neutral active inactive")
    void players_adminFilter_togglesThroughNeutralActiveInactive() {
        Session adminSession = createOnlineSession("admin-1", "admin", 1, true);
        Session player2 = createOnlineSession("player-2", "player2", 2, false);
        when(sessionService.streamCached()).thenAnswer(invocation -> Stream.of(session, adminSession, player2));

        playerMenu.players("viewer-1", 1);

        // Toggle to Active
        menuService.onMenuOption(session, 0);
        assertThat(session.sortStatus.get("admin")).isEqualTo(StatusEnum.Active);
        assertThat(session.activeScreen()).isNotNull();

        // Toggle to Inactive
        menuService.onMenuOption(session, 0);
        assertThat(session.sortStatus.get("admin")).isEqualTo(StatusEnum.Inactive);
        assertThat(session.activeScreen()).isNotNull();

        // Toggle to Neutral
        menuService.onMenuOption(session, 0);
        assertThat(session.sortStatus.get("admin")).isEqualTo(StatusEnum.Neutral);
        assertThat(session.activeScreen()).isNotNull();
    }

    @Test
    @DisplayName("players selecting a player opens routed profile via route history")
    void players_selectingPlayer_opensRoutedProfileViaRouteHistory() {
        Session adminSession = createOnlineSession("admin-1", "admin", 1, true);
        when(sessionService.streamCached()).thenAnswer(invocation -> Stream.of(session, adminSession));

        playerMenu.players("viewer-1", 1);

        // Page 1 layout with 2 players (perPage=2): admin-filter(0), select-0(1), select-1(2), back(3), close(4)
        menuService.onMenuOption(session, 1); // select admin player

        assertThat(session.hasHistory()).isFalse();
        assertThat(session.hasRouteHistory()).isTrue();
        assertThat(session.activeScreen()).isNotNull();
        assertThat(session.activeScreen().hasRoute()).isTrue();
        assertThat(session.activeScreen().route().id()).isEqualTo("player.profile");
        verify(gateway).menu(eq(session.player), eq(0), eq("player-menu-player-title"), any(), any());
    }
}
