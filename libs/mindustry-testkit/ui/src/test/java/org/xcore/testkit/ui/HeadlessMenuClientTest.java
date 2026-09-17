package org.xcore.testkit.ui;

import mindustry.ui.builder.UiBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recorded actual-client semantics the fake must reproduce
 * (hide notification is synchronous — proven by ActualDialogHideTest).
 */
class HeadlessMenuClientTest {
    private static final int MENU = 7;

    private static UiSnapshot body() {
        return UiSnapshot.capture(UiBuilder.table().add(UiBuilder.label("body")));
    }

    @Test
    void hideEmitsCancelWithCurrentTokenAndClosesWindowSynchronously() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 42, false, body());

        assertTrue(client.isVisible(MENU));
        client.hide(MENU);

        assertEquals(1, client.outbox().size());
        var choose = client.outbox().poll();
        assertEquals(42, choose.token());
        assertNull(choose.action());
        assertFalse(client.isVisible(MENU));
    }

    @Test
    void clickEmitsChooseWithTokenAndSuppressesLaterDismissCancel() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 42, false, body());

        client.click(MENU, "select:a");

        assertEquals(1, client.outbox().size());
        var choose = client.outbox().poll();
        assertEquals("select:a", choose.action());
        assertFalse(choose.isCancel());

        // recorded Menus.java semantics: any button click sets wasHidden=true,
        // so post-click Escape sends nothing (ActualDialogHideTest covers the
        // pre-click case where hide() does notify).
        client.dismiss(MENU);
        assertEquals(0, client.outbox().size());
        assertFalse(client.isVisible(MENU));
    }
}
