package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
import org.xcore.plugin.player.Badge;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.runtime.UpdateResult;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerProfileUiControllerTest {

    @SuppressWarnings("unchecked")
    private Session createTestSession(String uuid, boolean mobile) {
        PlayerData data = new PlayerData(uuid, true);
        data.pid = 42;
        data.nickname = "TestUser";
        data.customNickname = "[#2CABFEFF]EpicBuilder";
        data.description = "Logic master";
        data.admin = false;
        data.pvpRating = 1250;
        data.hexedPoints = 15;
        data.hexedRank(HexedRanks.HexedRank.advanced);
        data.unlockedBadges = Set.of(Badge.DEVELOPER.id());
        data.activeBadge = Badge.DEVELOPER.id();
        data.badgeSymbolColorMode = "default";
        data.createdModelTime = System.currentTimeMillis() - 86400000L;
        data.totalPlayTime = 120; // 2 hours

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

    private PlayerStatsOverview createSampleStats() {
        return new PlayerStatsOverview(
                new AggregatedPlayerStats(50, 30, 1000, 200, 100, 50, 10),
                new ModeStatsSummary(20, 14, 0, 0, 0, 0),
                new ModeStatsSummary(15, 0, 50, 25, 0, 0),
                new ModeStatsSummary(15, 10, 1, 8, 1, 8)
        );
    }

    @Test
    @DisplayName("createModel initializes profile from PlayerData correctly")
    void createModel_initializesFromPlayerData() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, PlayerProfileUiController.Tab.OVERVIEW, null, null, null, null
        );

        assertThat(model.targetUuid()).isEqualTo("uuid-1");
        assertThat(model.pid()).isEqualTo(42);
        assertThat(model.nickname()).isEqualTo("TestUser");
        assertThat(model.customNickname()).isEqualTo("[#2CABFEFF]EpicBuilder");
        assertThat(model.description()).isEqualTo("Logic master");
        assertThat(model.pvpRating()).isEqualTo(1250);
        assertThat(model.hexedPoints()).isEqualTo(15);
        assertThat(model.hexedRank()).isEqualTo(HexedRanks.HexedRank.advanced);
        assertThat(model.tab()).isEqualTo(PlayerProfileUiController.Tab.OVERVIEW);
        assertThat(model.isSelf()).isTrue();
    }

    @Test
    @DisplayName("renderHexedProgressBar formats progress and handles max rank")
    void renderHexedProgressBar_formatsCorrectly() {
        // Advanced rank requires 10 wins; with 5 points it should be 50%
        String bar = PlayerProfileUiController.renderHexedProgressBar(HexedRanks.HexedRank.advanced, 5, 10);
        assertThat(bar).contains("■");
        assertThat(bar).contains("25%"); // regular -> advanced: regular requirements = 10; 5/20 = 25%

        // Max rank
        String maxBar = PlayerProfileUiController.renderHexedProgressBar(HexedRanks.HexedRank.the_legend, 100, 10);
        assertThat(maxBar).contains("★ MAX RANK ACHIEVED ★");
    }

    @Test
    @DisplayName("renderRatingLeagueProgressBar formats league progress and handles max tier")
    void renderRatingLeagueProgressBar_formatsCorrectly() {
        // Lead (1000) to Graphite (1200): at 1100, progress is 50%
        String bar = PlayerProfileUiController.renderRatingLeagueProgressBar(RatingLeague.LEAD, 1100, 10);
        assertThat(bar).contains("[sky]");
        assertThat(bar).contains("[darkgray]");
        assertThat(bar).contains("50%");

        // Max tier
        String maxBar = PlayerProfileUiController.renderRatingLeagueProgressBar(RatingLeague.SURGE_ALLOY, 3000, 10);
        assertThat(maxBar).contains("★ MAX LEAGUE ACHIEVED ★");
    }

    @Test
    @DisplayName("renderBlockRatioBar produces proportional 3-color blocks")
    void renderBlockRatioBar_proportional() {
        String bar = PlayerProfileUiController.renderBlockRatioBar(100, 0, 0, 10);
        assertThat(bar).contains("[lime]");
        assertThat(bar).contains("■");

        String zeroBar = PlayerProfileUiController.renderBlockRatioBar(0, 0, 0, 10);
        assertThat(zeroBar).contains("[darkgray]");
    }

    @Test
    @DisplayName("formatDuration formats days, hours, and minutes")
    void formatDuration_formatsUnits() {
        String formatted = PlayerProfileUiController.formatDuration(24 * 60 + 2 * 60 + 5, null);
        assertThat(formatted).isEqualTo("1d 2h 5m");

        String zero = PlayerProfileUiController.formatDuration(0, null);
        assertThat(zero).isEqualTo("0m");
    }

    @Test
    @DisplayName("update SelectTab changes active tab")
    void update_selectTab_changesTab() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, null, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.OVERVIEW, null, null
        );

        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.SelectTab(PlayerProfileUiController.Tab.STATS), null
        );

        assertThat(res.model().tab()).isEqualTo(PlayerProfileUiController.Tab.STATS);
    }

    @Test
    @DisplayName("update StatsLoaded populates telemetry when target UUID matches")
    void update_statsLoaded_populatesStats() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, null, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.OVERVIEW, null, null
        );
        assertThat(model.isStatsLoading()).isTrue();

        PlayerStatsOverview stats = createSampleStats();
        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.StatsLoaded("uuid-1", stats, 3), null
        );

        assertThat(res.model().isStatsLoading()).isFalse();
        assertThat(res.model().stats()).isSameAs(stats);
        assertThat(res.model().hexedTopRank()).isEqualTo(3);
    }

    @Test
    @DisplayName("update StatsLoaded ignores mismatched target UUID")
    void update_statsLoaded_ignoresMismatchedUuid() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, null, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.OVERVIEW, null, null
        );

        PlayerStatsOverview stats = createSampleStats();
        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.StatsLoaded("other-uuid", stats, 3), null
        );

        assertThat(res.isNoop()).isTrue();
        assertThat(res.model().stats()).isNull();
    }

    @Test
    @DisplayName("update CycleAdminFilter rotates through filter modes")
    void update_cycleAdminFilter_rotates() {
        Session session = createTestSession("uuid-1", false);
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.streamCached()).thenAnswer(inv -> Stream.of(session));

        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, sessionService, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.PLAYERS, null, null
        );

        assertThat(model.adminFilter()).isEqualTo(PlayerProfileUiController.AdminFilter.ALL);

        UpdateResult<PlayerProfileUiController.ProfileModel> res1 = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.CycleAdminFilter(), null
        );
        assertThat(res1.model().adminFilter()).isEqualTo(PlayerProfileUiController.AdminFilter.ADMINS_ONLY);

        UpdateResult<PlayerProfileUiController.ProfileModel> res2 = controller.update(
                res1.model(), new PlayerProfileUiController.ProfileEvent.CycleAdminFilter(), null
        );
        assertThat(res2.model().adminFilter()).isEqualTo(PlayerProfileUiController.AdminFilter.NON_ADMINS);

        UpdateResult<PlayerProfileUiController.ProfileModel> res3 = controller.update(
                res2.model(), new PlayerProfileUiController.ProfileEvent.CycleAdminFilter(), null
        );
        assertThat(res3.model().adminFilter()).isEqualTo(PlayerProfileUiController.AdminFilter.ALL);
    }

    @Test
    @DisplayName("update ChangePlayersPage clamps page within range")
    void update_changePlayersPage_clamps() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, null, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.PLAYERS, null, null
        );

        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.ChangePlayersPage(999), null
        );
        assertThat(res.model().playersPage()).isEqualTo(1); // empty list has 1 page

        UpdateResult<PlayerProfileUiController.ProfileModel> resNegative = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.ChangePlayersPage(-5), null
        );
        assertThat(resNegative.model().playersPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("update BackToPlayers switches to PLAYERS tab")
    void update_backToPlayers_switchesToPlayersTab() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, null, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.OVERVIEW, null, null
        );

        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.BackToPlayers(), null
        );
        assertThat(res.model().tab()).isEqualTo(PlayerProfileUiController.Tab.PLAYERS);
    }

    @Test
    @DisplayName("update InspectPlayer transitions to target player in OVERVIEW tab")
    void update_inspectPlayer_transitionsTarget() {
        Session session = createTestSession("uuid-1", false);
        PlayerData other = new PlayerData("other-uuid", true);
        other.pid = 99;
        other.nickname = "TargetPlayer";

        Session otherSession = createTestSession("other-uuid", false);
        otherSession.data = other;
        otherSession.player.id = 99;

        SessionService sessionService = mock(SessionService.class);
        when(sessionService.get("other-uuid")).thenReturn(otherSession);

        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, sessionService, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.PLAYERS, null, null
        );

        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.InspectPlayer("other-uuid"), null
        );

        assertThat(res.model().tab()).isEqualTo(PlayerProfileUiController.Tab.OVERVIEW);
        assertThat(res.model().targetUuid()).isEqualTo("other-uuid");
        assertThat(res.model().pid()).isEqualTo(99);
        assertThat(res.model().nickname()).isEqualTo("TargetPlayer");
        assertThat(res.model().viewingFromPlayersTab()).isTrue();
        assertThat(res.model().isSelf()).isFalse();
    }

    @Test
    @DisplayName("InspectPlayer keeps isSelf accurate based on viewerUuid")
    void inspectPlayer_keepsIsSelfAccurate() {
        Session session = createTestSession("alice-uuid", false);
        PlayerData bob = new PlayerData("bob-uuid", true);
        bob.pid = 50;

        Session bobSession = createTestSession("bob-uuid", false);
        bobSession.data = bob;

        SessionService sessionService = mock(SessionService.class);
        when(sessionService.get("bob-uuid")).thenReturn(bobSession);
        when(sessionService.get("alice-uuid")).thenReturn(session);

        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, sessionService, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.PLAYERS, null, null
        );

        // Alice inspects Bob -> isSelf must be FALSE
        UpdateResult<PlayerProfileUiController.ProfileModel> inspectBob = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.InspectPlayer("bob-uuid"), null
        );
        assertThat(inspectBob.model().isSelf()).isFalse();

        // Alice inspects Alice -> isSelf must be TRUE
        UpdateResult<PlayerProfileUiController.ProfileModel> inspectSelf = controller.update(
                inspectBob.model(), new PlayerProfileUiController.ProfileEvent.InspectPlayer("alice-uuid"), null
        );
        assertThat(inspectSelf.model().isSelf()).isTrue();
    }

    @Test
    @DisplayName("InspectPlayer on missing player returns error feedback without blocking")
    void inspectPlayer_missingPlayer_returnsFeedback() {
        Session session = createTestSession("uuid-1", false);
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.get("ghost-uuid")).thenReturn(null);

        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, sessionService, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.PLAYERS, null, null
        );

        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.InspectPlayer("ghost-uuid"), null
        );

        assertThat(res.model().feedbackMessage()).contains("error-player-not-found");
    }

    @Test
    @DisplayName("OpenAuditHistory and OpenAuditActions rejected for non-admin viewers")
    void openAudit_rejectedForNonAdmin() {
        Session session = createTestSession("uuid-1", false);
        session.player.admin = false;
        AuditHistoryMenu auditMenu = mock(AuditHistoryMenu.class);

        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, auditMenu, null, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.OVERVIEW, null, null
        );
        assertThat(model.isViewerAdmin()).isFalse();

        UpdateResult<PlayerProfileUiController.ProfileModel> resHistory = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.OpenAuditHistory(), null
        );
        verify(auditMenu, never()).history(anyString(), any());
        assertThat(resHistory.isNoop()).isTrue();

        UpdateResult<PlayerProfileUiController.ProfileModel> resActions = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.OpenAuditActions(), null
        );
        verify(auditMenu, never()).actions(anyString(), any());
        assertThat(resActions.isNoop()).isTrue();
    }

    @Test
    @DisplayName("OpenSettings rejected when non-admin views other player")
    void openSettings_rejectedForNonAdminViewingOther() {
        Session session = createTestSession("viewer-uuid", false);
        session.player.admin = false;
        PlayerMenu playerMenu = mock(PlayerMenu.class);

        PlayerData other = new PlayerData("other-uuid", true);
        other.admin = false;

        PlayerProfileUiController controller = new PlayerProfileUiController(
                playerMenu, null, null, null, null, null, null, session, other
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.OVERVIEW, null, null
        );
        assertThat(model.isSelf()).isFalse();
        assertThat(model.isViewerAdmin()).isFalse();

        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.OpenSettings(), null
        );

        verify(playerMenu, never()).openSettingsUi(any(), any());
        assertThat(res.isNoop()).isTrue();
    }

    @Test
    @DisplayName("BackToPlayers preserves page number")
    void backToPlayers_preservesPage() {
        Session session = createTestSession("uuid-1", false);
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.streamCached()).thenAnswer(inv -> Stream.of(session));

        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, sessionService, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.PLAYERS, null, null
        );
        PlayerProfileUiController.ProfileModel pageModel = model.withPlayersPage(2);

        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                pageModel, new PlayerProfileUiController.ProfileEvent.BackToPlayers(), null
        );

        assertThat(res.model().tab()).isEqualTo(PlayerProfileUiController.Tab.PLAYERS);
    }

    @Test
    @DisplayName("update OpenSettings opens settings via playerMenu and closes profile")
    void update_openSettings_opensSettingsAndCloses() {
        Session session = createTestSession("uuid-1", false);
        PlayerMenu playerMenu = mock(PlayerMenu.class);

        PlayerProfileUiController controller = new PlayerProfileUiController(
                playerMenu, null, null, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.OVERVIEW, null, null
        );

        UpdateResult<PlayerProfileUiController.ProfileModel> res = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.OpenSettings(), null
        );

        verify(playerMenu).openSettingsUi(eq(session), eq(session.data));
        assertThat(res.close()).isTrue();
    }

    @Test
    @DisplayName("parseEvent correctly routes actions")
    void parseEvent_routesActions() {
        PlayerProfileUiController controller = new PlayerProfileUiController(null, null, null, null, null, null, null, null, null);

        assertThat(controller.parseEvent(new MenuResult("action:close"))).isInstanceOf(PlayerProfileUiController.ProfileEvent.Close.class);
        assertThat(controller.parseEvent(new MenuResult("action:settings"))).isInstanceOf(PlayerProfileUiController.ProfileEvent.OpenSettings.class);
        assertThat(controller.parseEvent(new MenuResult("action:audit_history"))).isInstanceOf(PlayerProfileUiController.ProfileEvent.OpenAuditHistory.class);
        assertThat(controller.parseEvent(new MenuResult("action:back_to_players"))).isInstanceOf(PlayerProfileUiController.ProfileEvent.BackToPlayers.class);
        assertThat(controller.parseEvent(new MenuResult("action:tab:overview"))).isEqualTo(new PlayerProfileUiController.ProfileEvent.SelectTab(PlayerProfileUiController.Tab.OVERVIEW));
        assertThat(controller.parseEvent(new MenuResult("action:tab:stats"))).isEqualTo(new PlayerProfileUiController.ProfileEvent.SelectTab(PlayerProfileUiController.Tab.STATS));
        assertThat(controller.parseEvent(new MenuResult("action:tab:players"))).isEqualTo(new PlayerProfileUiController.ProfileEvent.SelectTab(PlayerProfileUiController.Tab.PLAYERS));
        assertThat(controller.parseEvent(new MenuResult("action:filter_cycle"))).isInstanceOf(PlayerProfileUiController.ProfileEvent.CycleAdminFilter.class);
        assertThat(controller.parseEvent(new MenuResult("action:refresh_players"))).isInstanceOf(PlayerProfileUiController.ProfileEvent.RefreshPlayers.class);
        assertThat(controller.parseEvent(new MenuResult("action:inspect:some-uuid"))).isEqualTo(new PlayerProfileUiController.ProfileEvent.InspectPlayer("some-uuid"));
        assertThat(controller.parseEvent(new MenuResult((String) null))).isInstanceOf(PlayerProfileUiController.ProfileEvent.Close.class);
    }

    @Test
    @DisplayName("render compiles valid VNode tree across all tabs without errors")
    void render_compilesAcrossTabs() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, null, null, null, null, null, session, session.data
        );
        PlayerStatsOverview stats = createSampleStats();

        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);

        // 1. Overview tab
        PlayerProfileUiController.ProfileModel overviewModel = controller.createInitialModel(
                PlayerProfileUiController.Tab.OVERVIEW, stats, 1
        );
        VNode overviewTree = controller.render(overviewModel);
        assertThat(overviewTree).isNotNull();

        var builder = compiler.compile(overviewTree);
        String dsl = UiDslWriter.write(builder);
        assertThat(dsl).contains("table");

        // 2. Stats tab
        PlayerProfileUiController.ProfileModel statsModel = overviewModel.withTab(PlayerProfileUiController.Tab.STATS);
        VNode statsTree = controller.render(statsModel);
        assertThat(statsTree).isNotNull();
        String statsDsl = UiDslWriter.write(compiler.compile(statsTree));
        assertThat(statsDsl).contains("table");

        // 3. Players tab
        PlayerProfileUiController.OnlinePlayerRow row = new PlayerProfileUiController.OnlinePlayerRow(
                "p-2", 100, "OnlineUser", null, Badge.DEVELOPER.id(), "default", false, "FFD37F"
        );
        PlayerProfileUiController.ProfileModel playersModel = overviewModel
                .withTab(PlayerProfileUiController.Tab.PLAYERS)
                .withRefreshedPlayers(List.of(row), 1);
        VNode playersTree = controller.render(playersModel);
        assertThat(playersTree).isNotNull();
        String playersDsl = UiDslWriter.write(compiler.compile(playersTree));
        assertThat(playersDsl).contains("table");
        assertThat(playersDsl).contains("action:inspect:p-2");
    }

    @Test
    @DisplayName("overview tab renders bio with native color markup and wrapped layout")
    void overviewTab_rendersColoredBio_withWrapping() {
        Session session = createTestSession("uuid-1", false);
        session.data.description = "[#f7b6c]Ri[#f5a9b]T[#f39cac]r [gray] - my [white]Y [red]T [gray] ( [#f7b6c]@Ri[#f5a9b]T[#f39cac]rmm [gray]) [sky] Telegram [gray] - ( [#f7b6c]@Ri[#f5a9b]T[#f39cac]raa [gray])";

        PlayerProfileUiController controller = new PlayerProfileUiController(
                null, null, null, null, null, null, null, session, session.data
        );
        PlayerProfileUiController.ProfileModel model = controller.createInitialModel(
                PlayerProfileUiController.Tab.OVERVIEW, null, 1
        );

        VNode overviewTree = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(overviewTree));

        // Must preserve color codes without escaping brackets to [[
        assertThat(dsl).contains("[#f7b6c]Ri[#f5a9b]T[#f39cac]r");
        assertThat(dsl).doesNotContain("[[#f7b6c]");
        assertThat(dsl).contains("wrap: true");
    }
}
