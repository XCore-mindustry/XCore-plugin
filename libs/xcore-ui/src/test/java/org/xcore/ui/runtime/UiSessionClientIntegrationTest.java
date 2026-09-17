package org.xcore.ui.runtime;

import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;
import org.junit.jupiter.api.Test;
import org.xcore.testkit.ui.HeadlessMenuClient;
import org.xcore.testkit.ui.UiSnapshot;
import org.xcore.ui.LocalizerResolver;

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
            client.show(MENU, token, lastShow);
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

        // client clicks "+1" over the real wire record
        session.handle(new MenuResult("inc"));

        var patch = gateway.client.lastPatchDsl("slot_counter");
        assertThat(patch).contains("Count: 1");
    }
}
