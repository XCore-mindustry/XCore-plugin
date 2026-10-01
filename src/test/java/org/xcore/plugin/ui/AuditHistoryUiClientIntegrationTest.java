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
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditCursor;
import org.xcore.plugin.model.AuditDetails;
import org.xcore.plugin.model.AuditOrigin;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditRecordSummary;
import org.xcore.plugin.model.AuditTarget;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.Slice;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.AuditHistoryMenu;
import org.xcore.plugin.ui.menu.AuditHistoryUiController;
import org.xcore.testkit.ui.DeterministicUiLoop;
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.testkit.ui.UiWireMessage;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static mindustry.Vars.state;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditHistoryUiClientIntegrationTest {

    private GameState originalState;
    private DeterministicUiLoop loop;
    private MenuService menuService;
    private Session session;
    private SessionService sessionService;
    private AuditService auditService;
    private AuditHistoryMenu auditHistoryMenu;
    private PlayerData targetData;

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

        PlayerData viewerData = new PlayerData("viewer-uuid", true);
        viewerData.pid = 1;
        viewerData.nickname = "AdminAlex";

        targetData = new PlayerData("target-uuid", false);
        targetData.pid = 42;
        targetData.nickname = "TroubleMaker";

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
                                    boolean hideOnClick, boolean hideExisting, boolean fillScreen,
                                    NodeBuilder<?> ui) {
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

        auditService = mock(AuditService.class);
        auditHistoryMenu = new AuditHistoryMenu(
                new TomlSecretsConfig(),
                sessionService,
                auditService,
                menuService
        );

        Player player = Player.create();
        player.admin = true;
        player.con = mock(NetConnection.class);

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                menuService,
                mock(PlayerDataRepository.class),
                player,
                viewerData
        );

        when(sessionService.get(anyString())).thenReturn(session);

        loop.onClientMessage(msg -> {
            if (msg instanceof UiWireMessage.Choose choose) {
                var result = new MenuResult(choose.action());
                result.token = choose.token();
                menuService.onMenuBuilderResult(session, result);
            }
        });
    }

    @AfterEach
    void tearDown() {
        state = originalState;
        Core.app = null;
    }

    @Test
    @DisplayName("openAuditHistoryUi renders list, updates slots on filter, and opens record details")
    void openAuditHistoryUi_e2eInteraction() {
        AuditCursor nextCursor = new AuditCursor(50L, "audit-2");
        List<AuditRecordSummary> summaries = List.of(
                new AuditRecordSummary("audit-1", AuditAction.BAN, "TroubleMaker", "AdminAlex", "Griefing core", 3600000L, Instant.now().plusSeconds(3600), 1000L),
                new AuditRecordSummary("audit-2", AuditAction.MUTE, "TroubleMaker", "AdminAlex", "Chat flood", 600000L, Instant.now().minusSeconds(100), 500L)
        );
        when(auditService.findSummaryByTargetUuid(any(), any(), anyInt()))
                .thenReturn(new Slice<>(summaries, true, nextCursor));

        AuditRecord detailRecord = AuditRecord.builder()
                .auditId("audit-1")
                .action(AuditAction.BAN)
                .reason("Griefing core distribution lines repeatedly")
                .target(AuditTarget.builder().uuid("target-uuid").nameSnapshot("TroubleMaker").build())
                .actor(AuditActor.builder().nameSnapshot("AdminAlex").type(AuditActorType.PLAYER_ADMIN).build())
                .origin(AuditOrigin.builder().serverId("EU-Survival").build())
                .details(AuditDetails.builder().durationMs(3600000L).expiresAt(Instant.now().plusSeconds(3600)).build())
                .occurredAt(Instant.now())
                .build();
        when(auditService.findByAuditId("audit-1")).thenReturn(Optional.of(detailRecord));

        // 1. Server opens Audit History UI
        auditHistoryMenu.openAuditHistoryUi(session, targetData, AuditHistoryUiController.AuditViewMode.TARGET);
        int menuId = menuService.getMenuBuilderId();

        // 2. Deliver Show message to headless client
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(menuId)).isTrue();
        assertThat(session.hasActiveUiSession()).isTrue();

        // Verify wire DSL contains action buttons
        var showMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();
        String initialDsl = UiDslWriter.write((NodeBuilder<?>) showMsg.body().decode());

        assertThat(initialDsl).contains("action:filter:ALL");
        assertThat(initialDsl).contains("action:filter:BANS");
        assertThat(initialDsl).contains("action:filter:MUTES");
        assertThat(initialDsl).contains("action:inspect:audit-1");

        // 3. Client clicks BANS filter chip -> triggers slot update
        loop.client().click(menuId, "action:filter:BANS");
        assertThat(loop.stepClientToServer()).isTrue();
        while (loop.stepServerToClient()) {}

        var updateMsg = loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Update)
                .map(m -> (UiWireMessage.Update) m)
                .reduce((first, second) -> second)
                .orElseThrow();
        assertThat(updateMsg.targetId()).isIn("slot_audit_tabs", "slot_audit_list");

        // 4. Client clicks inspect record audit-1 -> transitions to in-dialog details
        loop.client().click(menuId, "action:inspect:audit-1");
        assertThat(loop.stepClientToServer()).isTrue();
        while (loop.stepServerToClient()) {}

        var detailsMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();
        String detailsDsl = UiDslWriter.write((NodeBuilder<?>) detailsMsg.body().decode());
        assertThat(detailsDsl).contains("action:back_to_list");
        assertThat(detailsDsl).contains("action:copy_id:audit-1");

        // 5. Client clicks Back to History
        loop.client().click(menuId, "action:back_to_list");
        assertThat(loop.stepClientToServer()).isTrue();
        while (loop.stepServerToClient()) {}

        var backMsg = (UiWireMessage.Show) loop.transcript().all().stream()
                .filter(m -> m instanceof UiWireMessage.Show)
                .reduce((first, second) -> second)
                .orElseThrow();
        String backDsl = UiDslWriter.write((NodeBuilder<?>) backMsg.body().decode());
        assertThat(backDsl).contains("action:filter:ALL");

        // 6. Client clicks Close
        loop.client().click(menuId, "action:close");
        assertThat(loop.stepClientToServer()).isTrue();
        while (loop.stepServerToClient()) {}

        assertThat(loop.client().isVisible(menuId)).isFalse();
    }
}
