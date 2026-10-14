package org.xcore.plugin.ui.menu;

import arc.Application;
import arc.Core;
import arc.mock.MockApplication;
import com.ospx.flubundle.Bundle;
import mindustry.game.Team;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.ui.builder.MenuResult;
import org.xcore.plugin.cases.ReportReason;
import org.xcore.plugin.cases.ReportService;
import org.xcore.plugin.cases.ReportTarget;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.MindustryMenuGateway;
import org.xcore.plugin.ui.kit.LayoutAssert;
import org.xcore.testkit.ui.DeterministicUiLoop;
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.testkit.ui.UiWireMessage;
import org.xcore.ui.runtime.UiSession;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** P3-only transport fixture: real MenuService/UiSession, deterministic wire/client and game queue. */
final class P3ReportUiFixture implements AutoCloseable {
    final Application previousApp = Core.app;
    final DeterministicUiLoop loop = new DeterministicUiLoop();
    final SessionService sessions = mock(SessionService.class);
    final ReportService reports = mock(ReportService.class);
    final MindustryMenuGateway gateway = mock(MindustryMenuGateway.class);
    final MenuService menus = new MenuService(() -> sessions, gateway);
    final Async async = new Async(mock(StorageExecutor.class), loop.serverPost()::post);
    final Player player = mock(Player.class);
    final Session session;
    final ReportMenu menu;
    Map<String, String> fields = Map.of();
    String lastDsl;

    P3ReportUiFixture() {
        Core.app = new MockApplication() {
            @Override public void post(Runnable action) { loop.serverPost().post(action); }
        };
        player.con = mock(NetConnection.class);
        when(player.con.isConnected()).thenReturn(true);
        when(player.isAdded()).thenReturn(true);
        when(player.uuid()).thenReturn("private-reporter-uuid");
        when(player.team()).thenReturn(Team.sharded);
        session = new Session(new TomlSecretsConfig(), Bundle.INSTANCE, menus, mock(PlayerDataRepository.class),
                player, new PlayerData(player.uuid(), true));
        session.localization = LayoutAssert.localization("en");
        when(sessions.getActive(player)).thenReturn(session);
        when(reports.enabled()).thenReturn(true);
        when(reports.reasons()).thenReturn(List.of(ReportReason.values()));
        when(reports.targets(session)).thenReturn(targets(9));
        doAnswer(invocation -> {
            int id = invocation.getArgument(1);
            long token = invocation.getArgument(2);
            UiSnapshot snapshot = UiSnapshot.capture(invocation.getArgument(7));
            lastDsl = mindustry.ui.builder.UiDslWriter.write(snapshot.decode());
            loop.sendServerToClient(new UiWireMessage.Show(id, token, invocation.getArgument(5), snapshot));
            return null;
        }).when(gateway).menuBuilder(eq(player), anyInt(), anyLong(), isNull(), anyBoolean(), anyBoolean(), anyBoolean(), any());
        doAnswer(invocation -> {
            loop.sendServerToClient(new UiWireMessage.Hide(invocation.getArgument(1)));
            return null;
        }).when(gateway).hideMenuBuilder(eq(player), anyInt());
        menus.init();
        loop.onClientMessage(message -> {
            if (message instanceof UiWireMessage.Choose choose && session.activeUiSession() != null) {
                MenuResult result = new MenuResult(choose.action());
                result.token = choose.token();
                fields.forEach(result.values::put);
                session.activeUiSession().handle(result);
            }
        });
        menu = new ReportMenu(reports, menus, sessions, async);
    }

    static List<ReportTarget> targets(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> new ReportTarget("private-target-uuid-" + i,
                i == 0 ? 0 : i == 1 ? -12 : i, "Player " + i, i < 4, i < 4 ? 0 : System.currentTimeMillis() - 120_000)).toList();
    }

    @SuppressWarnings("unchecked")
    UiSession<ReportUiController.Model, ReportUiController.Event> ui() {
        return (UiSession<ReportUiController.Model, ReportUiController.Event>) session.activeUiSession();
    }
    ReportUiController.Model model() { return ui().model(); }
    void open() { menu.open(session, null); drain(); }
    void click(String action) { click(action, null); }
    void click(String action, String comment) {
        fields = comment == null ? Map.of() : Map.of("field_comment", comment);
        loop.client().click(menus.getMenuBuilderId(), action);
        loop.flushClientOutbox();
        assertThat(loop.stepClientToServer()).isTrue();
        drain();
        fields = Map.of();
    }
    void confirm() {
        open(); click("action:pick:0"); click("action:reason:griefing"); click("action:next", "Broken core");
        assertThat(model().stage()).isEqualTo(ReportUiController.Stage.CONFIRM);
    }
    void drain() {
        for (int i = 0; i < 32; i++) {
            boolean delivered = loop.stepServerToClient();
            delivered |= loop.stepClientToServer();
            boolean posted = loop.serverPost().runNext();
            if (!delivered && !posted) return;
        }
        throw new AssertionError("P3 UI loop did not become idle");
    }
    long shows() { return loop.transcript().all().stream().filter(message -> message instanceof UiWireMessage.Show).count(); }
    @Override public void close() { Core.app = previousApp; }
}
