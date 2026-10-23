package org.xcore.testkit.ui;

import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.ui.Menus;
import mindustry.ui.builder.MenuResult;
import mindustry.ui.builder.UiBuilder;
import org.junit.jupiter.api.Test;
import org.xcore.testkit.fixtures.HeadlessWorld;
import org.xcore.testkit.fixtures.MockPlayer;
import org.xcore.testkit.fixtures.junit.WithHeadlessWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server's real {@code Call.menuBuilder*} path, end to end: packets leave through a
 * {@link MockPlayer}'s connection, and answers come back through {@code Menus.menuBuilderChoose}.
 */
@WithHeadlessWorld
class MockConnectionMenuBridgeTest {

    @Test
    void callPacketsReachTheClientAndAnswersReachTheMenuListener(HeadlessWorld world) {
        MockPlayer player = world.addPlayer("Client", Team.sharded);
        var loop = new DeterministicUiLoop();
        player.con().onSend(loop::sendPacket);
        loop.dispatchChoicesTo(player.player());

        List<MenuResult> answers = new ArrayList<>();
        int[] registered = new int[1];
        int menuId = registered[0] = Menus.registerMenuBuilder((who, result) -> {
            answers.add(result);
            if (result.is("action:rename")) {
                Call.menuBuilderUpdate(who.con, registered[0], "slot_name",
                        UiBuilder.table().add(UiBuilder.label("Name: " + result.getString("field_name"))));
            } else if (result.is("action:close")) {
                Call.hideMenuBuilder(who.con, registered[0]);
            }
        });

        Call.menuBuilder(player.con(), menuId, 31L, "Profile", false, true, false,
                UiBuilder.table().add(UiBuilder.label("Profile")));
        player.player().sendMessage("chat is not a menu packet");
        loop.runUntilIdle();
        assertTrue(loop.client().isVisible(menuId));
        assertTrue(loop.transcript().lastShow().dsl().contains("Profile"));

        loop.client().click(menuId, "action:rename", Map.of("field_name", "Osp"));
        loop.runUntilIdle();
        assertEquals("Osp", answers.get(0).getString("field_name"));
        assertEquals(31L, answers.get(0).token);
        assertTrue(loop.client().lastPatchDsl("slot_name").contains("Name: Osp"));

        loop.client().click(menuId, "action:close");
        loop.runUntilIdle();
        assertFalse(loop.client().isVisible(menuId));
        // The click marked the window hidden, so the server's hide sends no cancel back.
        assertEquals(2, answers.size());
        assertEquals(1, loop.transcript().all(UiWireMessage.Hide.class).size());
    }
}
