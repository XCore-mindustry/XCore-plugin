package org.xcore.testkit.ui;

import mindustry.ui.builder.UiBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicUiLoopTest {
    private static final int MENU = 11;

    private static UiSnapshot snapshot(String text) {
        return UiSnapshot.capture(UiBuilder.table().add(UiBuilder.label(text)));
    }

    @Test
    void messagesAreQueuedUntilExplicitlyStepped() {
        var loop = new DeterministicUiLoop();
        var client = loop.client();

        // 1. Post a server->client show message
        loop.sendServerToClient(new UiWireMessage.Show(MENU, 42L, false, snapshot("Hello")));

        // Message is queued; client has not received it yet
        assertFalse(client.isVisible(MENU));
        assertEquals(0, loop.transcript().size());

        // 2. Step the server->client direction
        boolean hadMessage = loop.stepServerToClient();
        assertTrue(hadMessage);
        assertTrue(client.isVisible(MENU));
        assertEquals(1, loop.transcript().size());
        assertTrue(loop.transcript().get(0) instanceof UiWireMessage.Show);

        // 3. Client clicks a button -> queues a client->server message
        client.click(MENU, "action:clicked");
        loop.flushClientOutbox();

        List<UiWireMessage.Choose> serverReceived = new ArrayList<>();
        loop.onClientMessage(msg -> {
            if (msg instanceof UiWireMessage.Choose choose) serverReceived.add(choose);
        });

        // Still queued, server has not received it
        assertTrue(serverReceived.isEmpty());

        // 4. Step client->server
        assertTrue(loop.stepClientToServer());
        assertEquals(1, serverReceived.size());
        assertEquals("action:clicked", serverReceived.get(0).action());
        assertEquals(42L, serverReceived.get(0).token());
        assertEquals(2, loop.transcript().size());
    }

    @Test
    void serverPostQueueDrainsExplicitly() {
        var loop = new DeterministicUiLoop();
        List<String> executed = new ArrayList<>();

        loop.serverPost().post(() -> executed.add("task1"));
        loop.serverPost().post(() -> executed.add("task2"));

        assertTrue(executed.isEmpty());

        loop.stepServerPost();
        assertEquals(List.of("task1", "task2"), executed);
    }
}
