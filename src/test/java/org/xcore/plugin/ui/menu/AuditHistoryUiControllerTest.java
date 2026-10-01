package org.xcore.plugin.ui.menu;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.Localizer;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
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
import org.xcore.plugin.service.moderation.DefaultAuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.VNode;
import org.xcore.ui.VNodeCompiler;
import org.xcore.ui.runtime.UpdateResult;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditHistoryUiControllerTest {

    private AuditHistoryMenu menu;
    private AuditService auditService;
    private SessionService sessionService;
    private Session session;
    private PlayerData viewerData;
    private PlayerData targetData;
    private Player player;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        menu = mock(AuditHistoryMenu.class);
        auditService = mock(DefaultAuditService.class);
        sessionService = mock(SessionService.class);

        viewerData = new PlayerData("viewer-uuid", true);
        viewerData.pid = 1;
        viewerData.nickname = "AdminAlex";

        targetData = new PlayerData("target-uuid", false);
        targetData.pid = 42;
        targetData.nickname = "TroubleMaker";
        targetData.discordId = "123456789";

        Bundle bundle = mock(Bundle.class);
        Localizer localizer = mock(Localizer.class);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString())).thenAnswer(i -> i.getArgument(0));
        when(localizer.format(anyString(), anyMap())).thenAnswer(i -> i.getArgument(0));
        when(bundle.localizer(any(java.util.function.Supplier.class))).thenReturn(localizer);

        player = Player.create();
        player.admin = true;
        player.con = mock(NetConnection.class);
        player.con.mobile = false;

        session = new Session(
                new TomlSecretsConfig(),
                bundle,
                null,
                mock(PlayerDataRepository.class),
                player,
                viewerData
        );
        when(sessionService.get("viewer-uuid")).thenReturn(session);
    }

    @Test
    @DisplayName("createInitialModel loads target sanctions page 1 and populates model")
    void createInitialModel_loadsTargetSanctions() {
        AuditCursor nextCursor = new AuditCursor(100L, "rec-2");
        List<AuditRecordSummary> records = List.of(
                new AuditRecordSummary("rec-1", AuditAction.BAN, "TroubleMaker", "AdminAlex", "Griefing", 3600000L, Instant.now().plusSeconds(3600), 1000L),
                new AuditRecordSummary("rec-2", AuditAction.WARN, "TroubleMaker", "ModSam", "Spam", null, null, 900L)
        );
        when(auditService.findSummaryByTargetUuid(eq("target-uuid"), any(), anyInt()))
                .thenReturn(new Slice<>(records, true, nextCursor));

        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);
        AuditHistoryUiController.AuditHistoryModel model = controller.createInitialModel(AuditHistoryUiController.AuditViewMode.TARGET);

        assertThat(model.mode()).isEqualTo(AuditHistoryUiController.AuditViewMode.TARGET);
        assertThat(model.actionFilter()).isEqualTo(AuditHistoryUiController.ActionFilter.ALL);
        assertThat(model.screen()).isEqualTo(AuditHistoryUiController.ViewScreen.LIST);
        assertThat(model.records()).hasSize(2);
        assertThat(model.pageIndex()).isEqualTo(1);
        assertThat(model.hasNext()).isTrue();
        assertThat(model.nextCursor()).isEqualTo(nextCursor);
        assertThat(model.cursorBackStack()).isEmpty();
    }

    @Test
    @DisplayName("update SelectMode switches to ACTOR mode and resets pagination")
    void update_selectMode_switchesToActor() {
        AuditCursor nextCursor = new AuditCursor(50L, "act-2");
        List<AuditRecordSummary> actions = List.of(
                new AuditRecordSummary("act-1", AuditAction.MUTE, "Victim1", "TroubleMaker", "Toxic", 600000L, Instant.now().plusSeconds(600), 500L)
        );
        DefaultAuditService defaultAudit = (DefaultAuditService) auditService;
        when(defaultAudit.findSummaryByActor(eq(AuditActorType.PLAYER_ADMIN), any(List.class), any(), anyInt()))
                .thenReturn(new Slice<>(actions, false, null));

        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);
        AuditHistoryUiController.AuditHistoryModel initial = controller.createInitialModel(AuditHistoryUiController.AuditViewMode.TARGET);

        UpdateResult<AuditHistoryUiController.AuditHistoryModel> res = controller.update(
                initial, new AuditHistoryUiController.AuditHistoryEvent.SelectMode(AuditHistoryUiController.AuditViewMode.ACTOR), null
        );

        assertThat(res.model().mode()).isEqualTo(AuditHistoryUiController.AuditViewMode.ACTOR);
        assertThat(res.model().records()).hasSize(1);
        assertThat(res.model().records().getFirst().action()).isEqualTo(AuditAction.MUTE);
        assertThat(res.model().pageIndex()).isEqualTo(1);
        assertThat(res.model().cursorBackStack()).isEmpty();
    }

    @Test
    @DisplayName("non-admin cannot switch view mode")
    void update_selectMode_ignoredForNonAdmin() {
        player.admin = false;
        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);
        AuditHistoryUiController.AuditHistoryModel initial = controller.createInitialModel(AuditHistoryUiController.AuditViewMode.TARGET);

        UpdateResult<AuditHistoryUiController.AuditHistoryModel> res = controller.update(
                initial, new AuditHistoryUiController.AuditHistoryEvent.SelectMode(AuditHistoryUiController.AuditViewMode.ACTOR), null
        );

        assertThat(res.model().mode()).isEqualTo(AuditHistoryUiController.AuditViewMode.TARGET);
    }

    @Test
    @DisplayName("update SelectFilter updates active filter")
    void update_selectFilter_updatesFilter() {
        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);
        AuditHistoryUiController.AuditHistoryModel initial = controller.createInitialModel(AuditHistoryUiController.AuditViewMode.TARGET);

        UpdateResult<AuditHistoryUiController.AuditHistoryModel> res = controller.update(
                initial, new AuditHistoryUiController.AuditHistoryEvent.SelectFilter(AuditHistoryUiController.ActionFilter.BANS), null
        );

        assertThat(res.model().actionFilter()).isEqualTo(AuditHistoryUiController.ActionFilter.BANS);
    }

    @Test
    @DisplayName("update NextPage pushes cursor and increments page index")
    void update_nextPage_pushesCursor() {
        AuditCursor cur1 = new AuditCursor(100L, "c1");
        AuditCursor cur2 = new AuditCursor(50L, "c2");

        when(auditService.findSummaryByTargetUuid(eq("target-uuid"), any(), anyInt()))
                .thenReturn(new Slice<>(List.of(new AuditRecordSummary("r1", AuditAction.BAN, "T", "A", "R", null, null, 1L)), true, cur2));

        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);
        AuditHistoryUiController.AuditHistoryModel initial = controller.createInitialModel(AuditHistoryUiController.AuditViewMode.TARGET);
        // Emulate having cursor cur1 and nextCursor cur2
        initial = initial.withPage(initial.records(), cur1, cur2, true, new ArrayDeque<>(), 1);

        UpdateResult<AuditHistoryUiController.AuditHistoryModel> res = controller.update(
                initial, new AuditHistoryUiController.AuditHistoryEvent.NextPage(), null
        );

        assertThat(res.model().pageIndex()).isEqualTo(2);
        assertThat(res.model().cursorBackStack()).containsExactly(cur1);
    }

    @Test
    @DisplayName("update PrevPage pops cursor and decrements page index")
    void update_prevPage_popsCursor() {
        AuditCursor cur1 = new AuditCursor(100L, "c1");
        AuditCursor cur2 = new AuditCursor(50L, "c2");

        when(auditService.findSummaryByTargetUuid(eq("target-uuid"), any(), anyInt()))
                .thenReturn(new Slice<>(List.of(new AuditRecordSummary("r1", AuditAction.BAN, "T", "A", "R", null, null, 1L)), true, cur2));

        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);
        AuditHistoryUiController.AuditHistoryModel initial = controller.createInitialModel(AuditHistoryUiController.AuditViewMode.TARGET);

        var stack = new ArrayDeque<AuditCursor>();
        stack.addLast(cur1);
        initial = initial.withPage(initial.records(), cur2, null, false, stack, 2);

        UpdateResult<AuditHistoryUiController.AuditHistoryModel> res = controller.update(
                initial, new AuditHistoryUiController.AuditHistoryEvent.PrevPage(), null
        );

        assertThat(res.model().pageIndex()).isEqualTo(1);
        assertThat(res.model().cursorBackStack()).isEmpty();
    }

    @Test
    @DisplayName("InspectRecord transitions to DETAILS and BackToList returns to LIST")
    void inspectRecord_andBackToList() {
        AuditRecord record = AuditRecord.builder()
                .auditId("audit-99")
                .action(AuditAction.BAN)
                .reason("Malicious griefing")
                .target(AuditTarget.builder().uuid("target-uuid").nameSnapshot("TroubleMaker").build())
                .actor(AuditActor.builder().nameSnapshot("AdminAlex").type(AuditActorType.PLAYER_ADMIN).build())
                .origin(AuditOrigin.builder().serverId("EU-Survival").build())
                .details(AuditDetails.builder().durationMs(86400000L).expiresAt(Instant.now().plusSeconds(86400)).build())
                .occurredAt(Instant.now())
                .build();
        when(auditService.findByAuditId("audit-99")).thenReturn(Optional.of(record));

        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);
        AuditHistoryUiController.AuditHistoryModel initial = controller.createInitialModel(AuditHistoryUiController.AuditViewMode.TARGET);

        UpdateResult<AuditHistoryUiController.AuditHistoryModel> resInspect = controller.update(
                initial, new AuditHistoryUiController.AuditHistoryEvent.InspectRecord("audit-99"), null
        );

        assertThat(resInspect.model().screen()).isEqualTo(AuditHistoryUiController.ViewScreen.DETAILS);
        assertThat(resInspect.model().inspectedAuditId()).isEqualTo("audit-99");
        assertThat(resInspect.model().inspectedRecord()).isEqualTo(record);

        UpdateResult<AuditHistoryUiController.AuditHistoryModel> resBack = controller.update(
                resInspect.model(), new AuditHistoryUiController.AuditHistoryEvent.BackToList(), null
        );

        assertThat(resBack.model().screen()).isEqualTo(AuditHistoryUiController.ViewScreen.LIST);
        assertThat(resBack.model().inspectedRecord()).isNull();
    }

    @Test
    @DisplayName("Back event pops session history stack when on LIST screen")
    void backEvent_popsSessionHistory() {
        AtomicBoolean historyRun = new AtomicBoolean(false);
        session.pushHistory(() -> historyRun.set(true));

        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);
        AuditHistoryUiController.AuditHistoryModel initial = controller.createInitialModel(AuditHistoryUiController.AuditViewMode.TARGET);

        UpdateResult<AuditHistoryUiController.AuditHistoryModel> res = controller.update(
                initial, new AuditHistoryUiController.AuditHistoryEvent.Back(), null
        );

        assertThat(res.close()).isTrue();
        assertThat(historyRun.get()).isTrue();
    }

    @Test
    @DisplayName("render compiles valid VNode tree for LIST and DETAILS views")
    void render_compilesValidVNodeTree() {
        AuditRecord record = AuditRecord.builder()
                .auditId("audit-123")
                .action(AuditAction.BAN)
                .reason("Test Reason [[with brackets]")
                .target(AuditTarget.builder().uuid("target-uuid").nameSnapshot("TroubleMaker").build())
                .actor(AuditActor.builder().nameSnapshot("AdminAlex").type(AuditActorType.PLAYER_ADMIN).build())
                .origin(AuditOrigin.builder().serverId("EU-Survival").build())
                .details(AuditDetails.builder().durationMs(86400000L).expiresAt(Instant.now().plusSeconds(86400)).build())
                .occurredAt(Instant.now())
                .build();
        when(auditService.findByAuditId("audit-123")).thenReturn(Optional.of(record));

        List<AuditRecordSummary> records = List.of(
                new AuditRecordSummary("audit-123", AuditAction.BAN, "TroubleMaker", "AdminAlex", "Test Reason [[with brackets]", 86400000L, Instant.now().plusSeconds(86400), 1000L),
                new AuditRecordSummary("audit-124", AuditAction.MUTE, "TroubleMaker", "AdminAlex", "Spamming chat", 3600000L, Instant.now().minusSeconds(100), 900L)
        );
        when(auditService.findSummaryByTargetUuid(any(), any(), anyInt()))
                .thenReturn(new Slice<>(records, false, null));

        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);
        AuditHistoryUiController.AuditHistoryModel listModel = controller.createInitialModel(AuditHistoryUiController.AuditViewMode.TARGET);

        VNode listNode = controller.render(listModel);
        assertThat(listNode).isNotNull();

        VNodeCompiler compiler = new VNodeCompiler(LocalizerResolver.IDENTITY);
        assertThat(compiler.compile(listNode)).isNotNull();

        AuditHistoryUiController.AuditHistoryModel detailsModel = listModel.withDetails("audit-123", record);
        VNode detailsNode = controller.render(detailsModel);
        assertThat(detailsNode).isNotNull();
        assertThat(compiler.compile(detailsNode)).isNotNull();
    }

    @Test
    @DisplayName("parseEvent handles action routes correctly")
    void parseEvent_handlesActions() {
        AuditHistoryUiController controller = new AuditHistoryUiController(menu, auditService, sessionService, session, targetData);

        assertThat(controller.parseEvent(new MenuResult("action:close")))
                .isInstanceOf(AuditHistoryUiController.AuditHistoryEvent.Close.class);
        assertThat(controller.parseEvent(new MenuResult("action:back")))
                .isInstanceOf(AuditHistoryUiController.AuditHistoryEvent.Back.class);
        assertThat(controller.parseEvent(new MenuResult("action:back_to_list")))
                .isInstanceOf(AuditHistoryUiController.AuditHistoryEvent.BackToList.class);
        assertThat(controller.parseEvent(new MenuResult("action:page:next")))
                .isInstanceOf(AuditHistoryUiController.AuditHistoryEvent.NextPage.class);
        assertThat(controller.parseEvent(new MenuResult("action:page:prev")))
                .isInstanceOf(AuditHistoryUiController.AuditHistoryEvent.PrevPage.class);
        assertThat(controller.parseEvent(new MenuResult("action:refresh")))
                .isInstanceOf(AuditHistoryUiController.AuditHistoryEvent.Refresh.class);

        var modeEvt = controller.parseEvent(new MenuResult("action:mode:ACTOR"));
        assertThat(modeEvt).isInstanceOf(AuditHistoryUiController.AuditHistoryEvent.SelectMode.class);
        assertThat(((AuditHistoryUiController.AuditHistoryEvent.SelectMode) modeEvt).mode())
                .isEqualTo(AuditHistoryUiController.AuditViewMode.ACTOR);

        var filterEvt = controller.parseEvent(new MenuResult("action:filter:MUTES"));
        assertThat(filterEvt).isInstanceOf(AuditHistoryUiController.AuditHistoryEvent.SelectFilter.class);
        assertThat(((AuditHistoryUiController.AuditHistoryEvent.SelectFilter) filterEvt).filter())
                .isEqualTo(AuditHistoryUiController.ActionFilter.MUTES);

        var inspectEvt = controller.parseEvent(new MenuResult("action:inspect:rec-55"));
        assertThat(inspectEvt).isInstanceOf(AuditHistoryUiController.AuditHistoryEvent.InspectRecord.class);
        assertThat(((AuditHistoryUiController.AuditHistoryEvent.InspectRecord) inspectEvt).auditId())
                .isEqualTo("rec-55");
    }
}
