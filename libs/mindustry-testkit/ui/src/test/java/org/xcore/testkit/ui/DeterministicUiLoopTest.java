package org.xcore.testkit.ui;

import arc.Core;
import mindustry.gen.HideMenuBuilderCallPacket;
import mindustry.gen.MenuBuilderCallPacket;
import mindustry.gen.MenuBuilderUpdateCallPacket;
import mindustry.gen.Player;
import mindustry.ui.Menus;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void menuPacketsBecomeWireMessagesCapturedAtSendTime() {
        var loop = new DeterministicUiLoop();
        var ui = UiBuilder.table().add(UiBuilder.label("sent"));
        MenuBuilderCallPacket show = MenuFixtures.packet(ui);
        show.id = MENU;
        show.token = 7L;
        assertTrue(loop.sendPacket(show));
        ui.add(UiBuilder.label("changed after send"));

        MenuBuilderUpdateCallPacket update = new MenuBuilderUpdateCallPacket();
        update.id = MENU;
        update.tableId = "slot";
        update.builder = UiBuilder.table().add(UiBuilder.label("patched"));
        assertTrue(loop.sendPacket(update));
        HideMenuBuilderCallPacket hide = new HideMenuBuilderCallPacket();
        hide.menuId = MENU;
        assertTrue(loop.sendPacket(hide));
        assertFalse(loop.sendPacket("not a menu packet"));

        assertEquals(3, loop.deliverToClient());
        UiWireMessage.Show shown = loop.transcript().lastShow();
        assertEquals(7L, shown.token());
        assertTrue(shown.hidePrevious());
        assertTrue(shown.dsl().contains("sent"));
        assertFalse(shown.dsl().contains("changed after send"));
        assertEquals("slot", loop.transcript().lastUpdate().targetId());
        assertTrue(loop.transcript().lastUpdate().dsl().contains("patched"));
        assertEquals(List.of(new UiWireMessage.Hide(MENU)), loop.transcript().all(UiWireMessage.Hide.class));
        assertFalse(loop.client().isVisible(MENU));
    }

    @Test
    void clickedFormValuesReachTheMenuResult() {
        var loop = new DeterministicUiLoop();
        List<MenuResult> received = new ArrayList<>();
        loop.onChoose(choose -> received.add(choose.toMenuResult()));
        loop.sendShow(MENU, 5L, false, UiBuilder.table().add(UiBuilder.label("form")));
        loop.deliverToClient();

        loop.client().click(MENU, "action:submit", Map.of("field_comment", "Broken core", "field_count", 3f));
        loop.runUntilIdle();

        assertEquals(1, received.size());
        MenuResult result = received.get(0);
        assertEquals("action:submit", result.result);
        assertEquals(5L, result.token);
        assertEquals("Broken core", result.getString("field_comment"));
        assertEquals(3f, result.getFloat("field_count"));
        assertEquals(Map.of("field_comment", "Broken core", "field_count", 3f),
                loop.transcript().chooses().get(0).values());
    }

    @Test
    void runUntilIdleSettlesARoundTripThroughServerPosts() {
        var loop = new DeterministicUiLoop();
        loop.onChoose(choose -> loop.serverPost().post(() ->
                loop.sendShow(MENU, 2L, true, UiBuilder.table().add(UiBuilder.label("after " + choose.action())))));
        loop.sendShow(MENU, 1L, false, UiBuilder.table().add(UiBuilder.label("first")));
        loop.runUntilIdle();

        loop.client().click(MENU, "action:next");
        assertTrue(loop.runUntilIdle() > 0);

        assertTrue(loop.isIdle());
        assertEquals(2, loop.transcript().shows().size());
        assertTrue(loop.transcript().lastShow().dsl().contains("after action:next"));
    }

    @Test
    void runUntilIdleFailsWhenTheServerNeverStopsPosting() {
        var loop = new DeterministicUiLoop();
        Runnable[] forever = new Runnable[1];
        forever[0] = () -> loop.serverPost().post(forever[0]);
        loop.serverPost().post(forever[0]);

        assertThrows(IllegalStateException.class, () -> loop.runUntilIdle(10));
    }

    @Test
    void lastShowNamesWhatIsMissing() {
        var error = assertThrows(IllegalStateException.class, () -> new DeterministicUiLoop().transcript().lastShow());
        assertTrue(error.getMessage().contains("No Show"), error.getMessage());
    }

    @Test
    void choicesAreDispatchedToTheRegisteredMenuListener() {
        var loop = new DeterministicUiLoop();
        Player player = Player.create();
        List<MenuResult> received = new ArrayList<>();
        int menuId = Menus.registerMenuBuilder((who, result) -> {
            assertSame(player, who);
            received.add(result);
        });
        loop.dispatchChoicesTo(player);
        loop.sendShow(menuId, 9L, false, UiBuilder.table().add(UiBuilder.label("menu")));
        loop.deliverToClient();

        loop.client().dismiss(menuId);
        loop.runUntilIdle();

        assertEquals(1, received.size());
        assertTrue(received.get(0).wasCancelled());
        assertEquals(9L, received.get(0).token);
    }

    @Test
    void installAsCoreAppQueuesPostsAndRestoresThePreviousApp() {
        var loop = new DeterministicUiLoop();
        var previous = Core.app;
        List<String> ran = new ArrayList<>();
        try (var ignored = loop.installAsCoreApp()) {
            Core.app.post(() -> ran.add("posted"));
            assertTrue(ran.isEmpty());
            assertEquals(1, loop.serverPost().size());
            loop.runUntilIdle();
        }
        assertEquals(List.of("posted"), ran);
        assertSame(previous, Core.app);
    }
}
