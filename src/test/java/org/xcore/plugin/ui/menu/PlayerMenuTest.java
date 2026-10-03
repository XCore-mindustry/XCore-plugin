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
    @DisplayName("player opens reactive profile UI")
    void player_opensReactiveProfileUi() {
        playerMenu.player("viewer-1", targetData);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
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

        verify(async).forPlayer(eq(session.player), any(), any());
        verify(gateway, times(2)).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("the profile fills in stats and mode sections once they have been read off the game thread")
    void player_loadsModeSections() {
        org.xcore.plugin.integration.profile.ProfileSectionRegistry sections =
                new org.xcore.plugin.integration.profile.ProfileSectionRegistry();
        sections.register(new org.xcore.plugin.integration.profile.ProfileSectionProvider() {
            @Override
            public String id() {
                return "duel";
            }

            @Override
            public java.util.Optional<org.xcore.plugin.integration.profile.ProfileSectionView> load(PlayerData target) {
                return java.util.Optional.of(local -> new org.xcore.plugin.integration.profile.ProfileSection(
                        "Duel for " + target.uuid, java.util.List.of(), java.util.List.of()));
            }
        });
        // The test player is not in the world, which forPlayer requires before it calls back.
        org.xcore.plugin.concurrent.Async async = mock(org.xcore.plugin.concurrent.Async.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.concurrent.Callable<?> task = invocation.getArgument(1);
            java.util.function.BiConsumer<Player, Object> callback = invocation.getArgument(2);
            callback.accept(invocation.getArgument(0), task.call());
            return null;
        }).when(async).forPlayer(any(), any(), any());
        PlayerMenu menu = new PlayerMenu(
                new TomlSecretsConfig(), sessionService, gameDataRepository, playerDataRepository, bundle,
                playerDisplayService, profileSettings, auditHistoryMenu, menuService, async, sections);
        when(playerDataRepository.findTopRank(any(), any())).thenReturn(7);

        menu.player("viewer-1", targetData);

        var model = (org.xcore.plugin.ui.menu.PlayerProfileUiController.ProfileModel) session.activeUiSession().model();
        assertThat(model.isStatsLoading()).isFalse();
        assertThat(model.hexedTopRank()).isEqualTo(7);
        assertThat(model.stats()).isNotNull();
        assertThat(model.sections()).hasSize(1);
        assertThat(model.sections().getFirst().render(null).headline()).isEqualTo("Duel for " + targetData.uuid);
    }

    @Test
    @DisplayName("players opens reactive profile UI on Players tab")
    void players_opensReactivePlayersTab() {
        playerMenu.players("viewer-1", 1);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
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
    @DisplayName("openProfileUi opens reactive UI for valid session")
    void openProfileUi_opensReactiveUi() {
        playerMenu.openProfileUi(session, targetData);

        verify(gateway).menuBuilder(eq(session.player), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }

    @Test
    @DisplayName("openProfileUi ignores null session or null player")
    void openProfileUi_ignoresNullSessionOrNullPlayer() {
        playerMenu.openProfileUi(null, targetData);
        playerMenu.openProfileUi(session, null);

        verify(gateway, never()).menuBuilder(any(), anyInt(), anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), any());
    }
}
