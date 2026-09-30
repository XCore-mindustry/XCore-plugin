package org.xcore.plugin.ui;

import arc.struct.Seq;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Provider;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
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
import static org.mockito.ArgumentMatchers.anyLong;
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

        TomlSecretsConfig secretsConfig = new TomlSecretsConfig();
        secretsConfig.pagination.eventsPerPage = 2;

        bundle = mock(Bundle.class);
        when(bundle.getAvailableLocales()).thenReturn(new Seq<>());

        playerDataRepository = mock(PlayerDataRepository.class);
        auditHistoryMenu = new AuditHistoryMenu(secretsConfig, sessionService, auditService, menuService);
        auditHistoryMenu.init();
        when(playerDisplayService.resolveBaseName(any(), any())).thenAnswer(invocation -> {
            PlayerData data = invocation.getArgument(0);
            return data.nickname;
        });

        playerMenu = new PlayerMenu(
                secretsConfig, sessionService,
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
    @DisplayName("preloaded target data is reused without a duplicate Mongo lookup")
    void player_preloadedTargetDataSkipsRepositoryLookup() {
        playerMenu.player("viewer-1", targetData);

        // The async caller (TopMenu/PlayerController) already loaded the target data;
        // route rendering must not issue a second findByUuid on the main thread.
        verify(playerDataRepository, never()).findByUuid(targetData.uuid);
        assertThat(session.activeScreen().route().id()).isEqualTo("player.profile");
    }

    @Test
    @DisplayName("player with Async prefetches stats and rank off main thread")
    void player_withAsync_prefetchesStatsAndRankOffMainThread() {
        Provider<SessionService> sessionProvider = mock(Provider.class);
        when(sessionProvider.get()).thenReturn(sessionService);
        MenuService freshMenuService = new MenuService(sessionProvider, gateway);

        org.xcore.plugin.concurrent.Async async = mock(org.xcore.plugin.concurrent.Async.class);
        PlayerMenu asyncMenu = new PlayerMenu(
                new TomlSecretsConfig(), sessionService,
                gameDataRepository,
                bundle,
                playerDisplayService, profileSettings,
                auditHistoryMenu,
                freshMenuService,
                async);

        org.mockito.Mockito.doAnswer(invocation -> {
            Player p = invocation.getArgument(0);
            java.util.concurrent.Callable<?> task = invocation.getArgument(1);
            java.util.function.BiConsumer<Player, Object> cb = invocation.getArgument(2);
            Object result = task.call();
            cb.accept(p, result);
            return null;
        }).when(async).forPlayer(any(), any(), any());

        asyncMenu.player("viewer-1", targetData);

        // Pre-fetched via async task
        verify(async).forPlayer(eq(session.player), any(), any());
        assertThat(session.activeScreen().route().id()).isEqualTo("player.profile");
    }

    @Test
    @DisplayName("player queries legacy hexed top rank for profile rendering")
    void player_rendersLegacyHexedRankAndTopRank() {
        targetData.hexedRank(org.xcore.plugin.gamemode.hexed.HexedRanks.HexedRank.veteran);
        targetData.hexedPoints = 23;
        when(playerDataRepository.findTopRank(org.xcore.plugin.model.enums.TopCategory.HEXED, targetData)).thenReturn(5);

        playerMenu.player("viewer-1", targetData);

        verify(playerDataRepository).findTopRank(org.xcore.plugin.model.enums.TopCategory.HEXED, targetData);
    }

    @Test
    @DisplayName("player settings navigation opens reactive settings UI")
    void player_settingsNavigation_opensSettingsUi() {
        playerMenu.player("viewer-1", targetData);

        menuService.onMenuOption(session, 0);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
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
    @DisplayName("settings opens reactive settings UI")
    void settings_opensSettingsUi() {
        playerMenu.settings("viewer-1", targetData);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("settings denies access for non-admin viewing another player")
    void settings_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.settings("viewer-1", otherData);

        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("chatSettings opens reactive settings UI on Chat tab")
    void chatSettings_opensChatTab() {
        playerMenu.chatSettings("viewer-1", targetData);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("badges opens reactive settings UI on Badges tab")
    void badges_opensBadgesTab() {
        playerMenu.badges("viewer-1", targetData);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("allBadges opens reactive settings UI on Badges tab")
    void allBadges_opensBadgesTab() {
        playerMenu.allBadges("viewer-1", targetData);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("languageSelectionMenu opens reactive settings UI on Chat tab")
    void languageSelectionMenu_opensChatTab() {
        playerMenu.languageSelectionMenu("viewer-1", targetData, false);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("badgeSymbolColorMode opens reactive settings UI on Badges tab")
    void badgeSymbolColorMode_opensBadgesTab() {
        playerMenu.badgeSymbolColorMode("viewer-1", targetData);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("chatSettings denies access for non-admin viewing another player")
    void chatSettings_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.chatSettings("viewer-1", otherData);

        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("badges denies access for non-admin viewing another player")
    void badges_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.badges("viewer-1", otherData);

        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("allBadges denies access for non-admin viewing another player")
    void allBadges_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.allBadges("viewer-1", otherData);

        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("openSettingsUi denies access for non-admin viewing another player")
    void openSettingsUi_accessDenied_forNonAdminViewingAnotherPlayer() {
        PlayerData otherData = new PlayerData("other-1", true);
        otherData.uuid = "other-1";

        playerMenu.openSettingsUi(session, otherData);

        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
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
                new TomlSecretsConfig(),
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
                new TomlSecretsConfig(),
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
