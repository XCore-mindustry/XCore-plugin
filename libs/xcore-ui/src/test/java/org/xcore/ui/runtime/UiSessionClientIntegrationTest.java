package org.xcore.ui.runtime;

import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;
import mindustry.ui.builder.UiDslWriter;
import org.junit.jupiter.api.Test;
import org.xcore.testkit.ui.DeterministicUiLoop;
import org.xcore.testkit.ui.HeadlessMenuClient;
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.testkit.ui.UiWireMessage;
import org.xcore.ui.LocalizerResolver;
import org.xcore.ui.Text;
import org.xcore.ui.Ui;
import org.xcore.ui.VNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end: real UiSession drives the testkit HeadlessMenuClient through a
 * gateway adapter; a client click comes back into the session and the next
 * render patches the slot. Reuses the counter controller from UiSessionTest
 * (package-private, same package).
 */
class UiSessionClientIntegrationTest {
    private static final int MENU = 3;

    private static class RecordingContext implements ControllerContext {
        boolean closed;
        @Override public String playerId() { return "uuid-int"; }
        @Override public void close() { closed = true; }
    }

    /** Bridges UiSession delivery into the fake client, capturing wire payloads. */
    private static class ClientGateway implements UiSession.DeliveryGateway {
        final HeadlessMenuClient client;
        UiSnapshot lastShow;

        ClientGateway(HeadlessMenuClient client) { this.client = client; }

        @Override
        public void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui) {
            lastShow = UiSnapshot.capture(ui);
            client.show(MENU, token, true, lastShow);
        }

        @Override
        public void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui) {
            client.update(MENU, elementId, UiSnapshot.capture(ui));
        }

        @Override
        public void hide(String playerId) {
            client.hide(MENU);
        }
    }

    @Test
    void sessionShowThenClientClickPatchesSlot() {
        var gateway = new ClientGateway(new HeadlessMenuClient());
        var controller = new UiSessionTest.CounterController();
        var ctx = new RecordingContext();

        UiSession<UiSessionTest.TestModel, UiSessionTest.TestEvent> session = UiSession.start(
                controller, controller.initialModel(null), ctx, gateway, LocalizerResolver.IDENTITY);
        session.open();

        assertThat(gateway.lastShow).isNotNull();

        gateway.client.click(MENU, "inc");
        deliverNext(gateway.client, session);

        var patch = gateway.client.lastPatchDsl("slot_counter");
        assertThat(patch).contains("Count: 1");
    }

    private static void deliverNext(HeadlessMenuClient client, UiSession<?, ?> session) {
        var choose = client.outbox().poll();
        assertThat(choose).isNotNull();
        assertThat(choose.menuId()).isEqualTo(MENU);
        var result = new MenuResult(choose.action());
        result.token = choose.token();
        session.handle(result);
    }

    record RModel(String text) {}

    sealed interface REvent {
        record Rerender() implements REvent {}
        record Close() implements REvent {}
    }

    /** Synthetic cancel-ignoring policy; this is NOT a regression test of MapUiController. */
    private static class RerenderController implements UiController<RModel, REvent> {
        @Override public RModel initialModel(Object context) { return new RModel("v1"); }

        @Override
        public UpdateResult<RModel> update(RModel model, REvent event, ControllerContext ctx) {
            return switch (event) {
                case REvent.Rerender() -> UpdateResult.rerender(new RModel("rerendered"));
                case REvent.Close() -> UpdateResult.close(model);
            };
        }

        @Override
        public VNode render(RModel model) {
            return Ui.table(t -> {
                t.slot("content", s -> s.label(Text.raw(model.text())));
                t.row();
                t.button(Text.raw("rerender"), "rerender", null);
                t.button(Text.raw("close"), "close", null);
            });
        }

        @Override
        public REvent parseEvent(MenuResult result) {
            if (result.wasCancelled()) return null;
            if ("rerender".equals(result.result)) return new REvent.Rerender();
            if ("close".equals(result.result)) return new REvent.Close();
            return null;
        }
    }

    @Test
    void fullRerenderReplacementEmitsOldWindowCancelAndSurvivesItsReturn() {
        var client = new HeadlessMenuClient();
        var gateway = new ClientGateway(client);
        var controller = new RerenderController();
        var ctx = new RecordingContext();

        UiSession<RModel, REvent> session = UiSession.start(
                controller, controller.initialModel(null), ctx, gateway, LocalizerResolver.IDENTITY);
        session.open();

        long token = session.token();

        // async details arrive -> full rerender: server re-shows the SAME menuId with the SAME token
        session.dispatch(new REvent.Rerender());

        // production MenuService always sends hideExisting=true; the client must
        // hide the old instance and report its cancel (recorded Menus.java semantics).
        assertThat(client.outbox()).hasSize(1);
        var cancel = client.outbox().peek();
        assertThat(cancel.isCancel()).isTrue();
        assertThat(cancel.token()).isEqualTo(token); // same token: token-check alone cannot cure this

        // the old window's cancel comes back from the network
        deliverNext(client, session);

        // The synthetic controller ignores cancel; the replacement remains visible.
        assertThat(client.isVisible(MENU)).isTrue();
        assertThat(client.outbox()).isEmpty();
        assertThat(UiDslWriter.write(gateway.lastShow.decode())).contains("rerendered");
        assertThat(session.model().text()).isEqualTo("rerendered");
    }

    @Test
    void sessionShowAndClientClickViaDeterministicLoop() {
        var loop = new DeterministicUiLoop();
        var gateway = new UiSession.DeliveryGateway() {
            @Override
            public void show(String playerId, long token, UiBuilder.NodeBuilder<?> ui) {
                loop.sendServerToClient(new UiWireMessage.Show(MENU, token, true, UiSnapshot.capture(ui)));
            }

            @Override
            public void update(String playerId, long token, String elementId, UiBuilder.NodeBuilder<?> ui) {
                loop.sendServerToClient(new UiWireMessage.Update(MENU, elementId, UiSnapshot.capture(ui)));
            }

            @Override
            public void hide(String playerId) {
                loop.sendServerToClient(new UiWireMessage.Hide(MENU));
            }
        };

        var controller = new UiSessionTest.CounterController();
        var ctx = new RecordingContext();
        UiSession<UiSessionTest.TestModel, UiSessionTest.TestEvent> session = UiSession.start(
                controller, controller.initialModel(null), ctx, gateway, LocalizerResolver.IDENTITY);

        loop.onClientMessage(msg -> {
            if (msg instanceof UiWireMessage.Choose choose) {
                var result = new MenuResult(choose.action());
                result.token = choose.token();
                session.handle(result);
            }
        });

        // 1. session.open() emits Show into the wire queue
        session.open();
        assertThat(loop.client().isVisible(MENU)).isFalse();

        // 2. Step server->client: dialog becomes visible on client
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().isVisible(MENU)).isTrue();

        // 3. Client clicks "+1": message is queued, server has not received it
        loop.client().click(MENU, "inc");
        assertThat(loop.client().lastPatchDsl("slot_counter")).isNull();

        // 4. Step client->server: server processes "inc" and queues Update, but client hasn't received it yet
        assertThat(loop.stepClientToServer()).isTrue();
        assertThat(loop.client().lastPatchDsl("slot_counter")).isNull();

        // 5. Step server->client: Update is delivered and client slot is patched
        assertThat(loop.stepServerToClient()).isTrue();
        assertThat(loop.client().lastPatchDsl("slot_counter")).contains("Count: 1");
    }
}
