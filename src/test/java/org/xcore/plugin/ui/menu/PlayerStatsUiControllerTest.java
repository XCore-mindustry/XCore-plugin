package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerStatsUiControllerTest {

    @SuppressWarnings("unchecked")
    private Session createTestSession(String uuid) {
        PlayerData data = new PlayerData(uuid, true);
        data.nickname = "TestMaster";
        data.customNickname = "MasterChief";
        data.pid = 42;
        data.description = "Core defender";
        data.pvpRating = 1500;
        data.hexedPoints = 120;

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
                null,
                data
        );
    }

    private PlayerStatsOverview createTestOverview() {
        AggregatedPlayerStats overall = new AggregatedPlayerStats(50, 35, 10000, 2000, 1500, 300, 150);
        ModeStatsSummary pvp = new ModeStatsSummary(20, 15, 75, 1, 15, 0);
        ModeStatsSummary surv = new ModeStatsSummary(20, 15, 75, 50, 30, 0);
        ModeStatsSummary hex = new ModeStatsSummary(10, 5, 50, 1, 8, 0);
        return new PlayerStatsOverview(overall, pvp, surv, hex);
    }

    @Test
    @DisplayName("createModel formats player data and match metrics cleanly")
    void createModel_formatsPlayerDataCleanly() {
        Session session = createTestSession("uuid-1");
        PlayerStatsOverview overview = createTestOverview();

        PlayerStatsUiController.StatsModel model = PlayerStatsUiController.createModel(
                null, session, session.data, overview, 3
        );

        assertThat(model.targetUuid()).isEqualTo("uuid-1");
        assertThat(model.customNickname()).isEqualTo("MasterChief");
        assertThat(model.pid()).isEqualTo(42);
        assertThat(model.description()).isEqualTo("Core defender");
        assertThat(model.pvpRating()).isEqualTo(1500);
        assertThat(model.gamesPlayed()).isEqualTo(50);
        assertThat(model.gamesWon()).isEqualTo(35);
        assertThat(model.winRatePercent()).isEqualTo(70);
        assertThat(model.blocksBuilt()).isEqualTo(10000);
        assertThat(model.hexedTopRank()).isEqualTo("#3");
        assertThat(model.isOwner()).isTrue();
        assertThat(model.activeTab()).isEqualTo("overview");
    }

    @Test
    @DisplayName("render produces VNode tree with tab bar and dynamic slot body")
    void render_producesVNodeTreeWithSlotBody() {
        Session session = createTestSession("uuid-1");
        PlayerStatsOverview overview = createTestOverview();
        PlayerStatsUiController.StatsModel model = PlayerStatsUiController.createModel(
                null, session, session.data, overview, 3
        );

        PlayerStatsUiController controller = new PlayerStatsUiController(null, null, session, session.data);
        VNode root = controller.render(model);

        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        String dsl = UiDslWriter.write(compiler.compile(root));

        // Header
        assertThat(dsl).contains("MasterChief");
        assertThat(dsl).contains("#42");
        assertThat(dsl).contains("Core defender");

        // Tab buttons
        assertThat(dsl).contains("tab:overview");
        assertThat(dsl).contains("tab:matches");
        assertThat(dsl).contains("tab:blocks");

        // Dynamic slot body with overview stats
        assertThat(dsl).contains("id: slot_tab_body");
        assertThat(dsl).contains("MiniPvP Rating:");
        assertThat(dsl).contains("[sky]1500[]");

        // Action buttons
        assertThat(dsl).contains("action:settings");
        assertThat(dsl).contains("action:players");
        assertThat(dsl).contains("action:close");
    }

    @Test
    @DisplayName("update with SelectTab returns in-place slot patch without full re-render")
    void update_selectTab_returnsInPlaceSlotPatch() {
        Session session = createTestSession("uuid-1");
        PlayerStatsUiController.StatsModel model = PlayerStatsUiController.createModel(
                null, session, session.data, createTestOverview(), 3
        );
        PlayerStatsUiController controller = new PlayerStatsUiController(null, null, session, session.data);

        UpdateResult<PlayerStatsUiController.StatsModel> result = controller.update(
                model, new PlayerStatsUiController.StatsEvent.SelectTab("matches"), null
        );

        assertThat(result.model().activeTab()).isEqualTo("matches");
        assertThat(result.dirtySlots()).containsExactly(PlayerStatsUiController.SLOT_TAB_BODY);
        assertThat(result.fullRerender()).isFalse();
        assertThat(result.close()).isFalse();
    }

    @Test
    @DisplayName("update with Close triggers context close and returns close directive")
    void update_close_triggersContextClose() {
        Session session = createTestSession("uuid-1");
        PlayerStatsUiController.StatsModel model = PlayerStatsUiController.createModel(
                null, session, session.data, createTestOverview(), 3
        );
        PlayerStatsUiController controller = new PlayerStatsUiController(null, null, session, session.data);

        AtomicBoolean closed = new AtomicBoolean(false);
        ControllerContext ctx = new ControllerContext() {
            @Override
            public String playerId() {
                return "uuid-1";
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };

        UpdateResult<PlayerStatsUiController.StatsModel> result = controller.update(
                model, new PlayerStatsUiController.StatsEvent.Close(), ctx
        );

        assertThat(closed.get()).isTrue();
        assertThat(result.close()).isTrue();
    }

    @Test
    @DisplayName("parseEvent maps MenuResult action strings to typed events")
    void parseEvent_mapsActionStringsToTypedEvents() {
        PlayerStatsUiController controller = new PlayerStatsUiController(null, null, null, null);

        MenuResult tabMatches = new MenuResult("tab:matches");
        assertThat(controller.parseEvent(tabMatches))
                .isEqualTo(new PlayerStatsUiController.StatsEvent.SelectTab("matches"));

        MenuResult close = new MenuResult("action:close");
        assertThat(controller.parseEvent(close))
                .isEqualTo(new PlayerStatsUiController.StatsEvent.Close());

        MenuResult settings = new MenuResult("action:settings");
        assertThat(controller.parseEvent(settings))
                .isEqualTo(new PlayerStatsUiController.StatsEvent.OpenSettings());
    }
}
