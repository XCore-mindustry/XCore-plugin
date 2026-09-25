package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.AggregatedPlayerStats;
import org.xcore.plugin.model.ModeStatsSummary;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerStatsOverview;
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

class PlayerProfileUiControllerTest {

    @SuppressWarnings("unchecked")
    private Session createTestSession(String uuid, boolean admin) {
        PlayerData data = new PlayerData(uuid, true);
        data.pid = 42;
        data.nickname = "Anuke";
        data.customNickname = "Architect";
        data.description = "Creator of Mindustry";
        data.pvpRating = 1500;
        data.hexedPoints = 25;
        data.totalPlayTime = 120;
        data.admin = admin;
        data.discordUsername = "anuke_dev";

        Player player = Player.create();
        player.admin = admin;

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
                player,
                data
        );
    }

    private PlayerStatsOverview createSampleStats() {
        return new PlayerStatsOverview(
                new AggregatedPlayerStats(100, 75, 5000, 1000, 300, 50, 120),
                new ModeStatsSummary(50, 40, 0, 0, 0, 0),
                new ModeStatsSummary(30, 20, 150, 80, 0, 0),
                new ModeStatsSummary(20, 15, 0, 0, 1, 18)
        );
    }

    @Test
    @DisplayName("createModel initializes profile model correctly from PlayerData")
    void createModel_initializesCorrectly() {
        Session session = createTestSession("uuid-1", false);
        PlayerStatsOverview stats = createSampleStats();

        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, stats, 1, null, null
        );

        assertThat(model.targetUuid()).isEqualTo("uuid-1");
        assertThat(model.targetPid()).isEqualTo(42);
        assertThat(model.nickname()).isEqualTo("Anuke");
        assertThat(model.customNickname()).isEqualTo("Architect");
        assertThat(model.description()).isEqualTo("Creator of Mindustry");
        assertThat(model.pvpRating()).isEqualTo(1500);
        assertThat(model.hexedPoints()).isEqualTo(25);
        assertThat(model.hexedTopRank()).isEqualTo("#1");
        assertThat(model.discordUsername()).isEqualTo("anuke_dev");
        assertThat(model.activeTab()).isEqualTo(PlayerProfileUiController.ProfileTab.OVERVIEW);
        assertThat(model.isOwner()).isTrue();
        assertThat(model.viewerIsAdmin()).isFalse();
    }

    @Test
    @DisplayName("render produces responsive DSL with 580 width, maxHeight 360 pane, and tabs")
    void render_producesResponsiveLayout() {
        Session session = createTestSession("uuid-1", true);
        PlayerStatsOverview stats = createSampleStats();
        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, stats, 1, null, null
        );

        PlayerProfileUiController controller = new PlayerProfileUiController(null, null, session, session.data);
        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        // Outer styling
        assertThat(dsl).contains("background: pane");
        assertThat(dsl).contains("width: 580");

        // Close button in header
        assertThat(dsl).contains("action:close");
        assertThat(dsl).contains("size: 34");

        // Tab buttons
        assertThat(dsl).contains("action:tab:overview");
        assertThat(dsl).contains("action:tab:stats");
        assertThat(dsl).contains("action:tab:modes");

        // ScrollPane with 360 maxHeight
        assertThat(dsl).contains("pane{");
        assertThat(dsl).contains("maxHeight: 360");

        // Overview content
        assertThat(dsl).contains("#42");
        assertThat(dsl).contains("@anuke_dev");
        assertThat(dsl).contains("Creator of Mindustry");

        // Action buttons
        assertThat(dsl).contains("action:settings");
        assertThat(dsl).contains("action:audit");
        assertThat(dsl).contains("action:players");
    }

    @Test
    @DisplayName("render Stats tab renders aggregated gameplay metrics")
    void render_statsTab_rendersMetrics() {
        Session session = createTestSession("uuid-1", false);
        PlayerStatsOverview stats = createSampleStats();
        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, stats, 1, null, null
        ).withActiveTab(PlayerProfileUiController.ProfileTab.STATS);

        PlayerProfileUiController controller = new PlayerProfileUiController(null, null, session, session.data);
        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("Overall Statistics");
        assertThat(dsl).contains("PvP Rating:[] [gold]1500");
        assertThat(dsl).contains("Win Rate:[] [accent]75%");
        assertThat(dsl).contains("Games Played:[] [white]100");
        assertThat(dsl).contains("Games Won:[] [white]75");
        assertThat(dsl).contains("Blocks Built:[] [white]5000");
    }

    @Test
    @DisplayName("render Modes tab renders PvP, Survival, and Hexed cards")
    void render_modesTab_rendersCards() {
        Session session = createTestSession("uuid-1", false);
        PlayerStatsOverview stats = createSampleStats();
        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, stats, 1, null, null
        ).withActiveTab(PlayerProfileUiController.ProfileTab.MODES);

        PlayerProfileUiController controller = new PlayerProfileUiController(null, null, session, session.data);
        VNode root = controller.render(model);
        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        assertThat(dsl).contains("⚔ PvP");
        assertThat(dsl).contains("🛡 Survival");
        assertThat(dsl).contains("👑 Hexed");
        assertThat(dsl).contains("Best Wave:[] [white]150");
        assertThat(dsl).contains("Best Place:[] [gold]#1");
    }

    @Test
    @DisplayName("update SelectTab marks SLOT_CONTENT dirty with zero full-dialog flicker")
    void update_selectTab_marksContentSlotDirty() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, null, null, null, null
        );
        PlayerProfileUiController controller = new PlayerProfileUiController(null, null, session, session.data);

        UpdateResult<PlayerProfileUiController.ProfileModel> result = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.SelectTab(PlayerProfileUiController.ProfileTab.STATS), null
        );

        assertThat(result.model().activeTab()).isEqualTo(PlayerProfileUiController.ProfileTab.STATS);
        assertThat(result.dirtySlots()).containsExactly(PlayerProfileUiController.SLOT_CONTENT);
    }

    @Test
    @DisplayName("update OpenSettings redirects to PlayerMenu.openSettingsUi")
    void update_openSettings_redirectsToSettings() {
        Session session = createTestSession("uuid-1", false);
        PlayerMenu menu = mock(PlayerMenu.class);
        PlayerProfileUiController controller = new PlayerProfileUiController(menu, null, session, session.data);
        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, null, null, null, null
        );

        controller.update(model, new PlayerProfileUiController.ProfileEvent.OpenSettings(), null);

        verify(menu).openSettingsUi(eq(session), eq(session.data));
    }

    @Test
    @DisplayName("update OpenAudit redirects to AuditHistoryMenu.history")
    void update_openAudit_redirectsToAudit() {
        Session session = createTestSession("uuid-1", true);
        AuditHistoryMenu auditMenu = mock(AuditHistoryMenu.class);
        PlayerProfileUiController controller = new PlayerProfileUiController(null, auditMenu, session, session.data);
        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, null, null, null, null
        );

        controller.update(model, new PlayerProfileUiController.ProfileEvent.OpenAudit(), null);

        verify(auditMenu).history(eq("uuid-1"), eq(session.data));
    }

    @Test
    @DisplayName("update OpenPlayers redirects to PlayerMenu.players")
    void update_openPlayers_redirectsToPlayers() {
        Session session = createTestSession("uuid-1", false);
        PlayerMenu menu = mock(PlayerMenu.class);
        PlayerProfileUiController controller = new PlayerProfileUiController(menu, null, session, session.data);
        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, null, null, null, null
        );

        controller.update(model, new PlayerProfileUiController.ProfileEvent.OpenPlayers(), null);

        verify(menu).players(eq("uuid-1"), eq(1));
    }

    @Test
    @DisplayName("update Close returns close UpdateResult")
    void update_close_returnsCloseResult() {
        Session session = createTestSession("uuid-1", false);
        PlayerProfileUiController controller = new PlayerProfileUiController(null, null, session, session.data);
        PlayerProfileUiController.ProfileModel model = PlayerProfileUiController.createModel(
                session, session.data, null, null, null, null
        );

        UpdateResult<PlayerProfileUiController.ProfileModel> result = controller.update(
                model, new PlayerProfileUiController.ProfileEvent.Close(), null
        );

        assertThat(result.close()).isTrue();
    }

    @Test
    @DisplayName("parseEvent handles actions and cancellation properly")
    void parseEvent_handlesActions() {
        PlayerProfileUiController controller = new PlayerProfileUiController(null, null, null, null);

        assertThat(controller.parseEvent(new MenuResult("action:tab:overview")))
                .isEqualTo(new PlayerProfileUiController.ProfileEvent.SelectTab(PlayerProfileUiController.ProfileTab.OVERVIEW));
        assertThat(controller.parseEvent(new MenuResult("action:tab:stats")))
                .isEqualTo(new PlayerProfileUiController.ProfileEvent.SelectTab(PlayerProfileUiController.ProfileTab.STATS));
        assertThat(controller.parseEvent(new MenuResult("action:tab:modes")))
                .isEqualTo(new PlayerProfileUiController.ProfileEvent.SelectTab(PlayerProfileUiController.ProfileTab.MODES));

        assertThat(controller.parseEvent(new MenuResult("action:settings")))
                .isInstanceOf(PlayerProfileUiController.ProfileEvent.OpenSettings.class);
        assertThat(controller.parseEvent(new MenuResult("action:audit")))
                .isInstanceOf(PlayerProfileUiController.ProfileEvent.OpenAudit.class);
        assertThat(controller.parseEvent(new MenuResult("action:players")))
                .isInstanceOf(PlayerProfileUiController.ProfileEvent.OpenPlayers.class);
        assertThat(controller.parseEvent(new MenuResult("action:close")))
                .isInstanceOf(PlayerProfileUiController.ProfileEvent.Close.class);
        assertThat(controller.parseEvent(new MenuResult((String) null)))
                .isInstanceOf(PlayerProfileUiController.ProfileEvent.Close.class);
    }
}
