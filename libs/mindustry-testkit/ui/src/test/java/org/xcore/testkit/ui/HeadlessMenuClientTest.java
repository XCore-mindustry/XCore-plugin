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
    void serverHideAfterClickDoesNotEmitCancel() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 42, true, body());
        client.click(MENU, "select:a");
        client.outbox().remove();

        client.hide(MENU);

        assertTrue(client.outbox().isEmpty());
        assertFalse(client.isVisible(MENU));
    }

    @Test
    void replacementAfterClickSuppressesOldCancelButNewWindowCanCancel() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 42, true, body());
        client.click(MENU, "select:a");
        client.outbox().remove();

        client.show(MENU, 42, true, body());

        assertTrue(client.outbox().isEmpty());
        assertTrue(client.isVisible(MENU));
        client.dismiss(MENU);
        assertEquals(1, client.outbox().size());
        var cancel = client.outbox().remove();
        assertTrue(cancel.isCancel());
        assertEquals(42, cancel.token());
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

    // ---- what the client builds, each confirmed on the real client by ActualMenusOracleTest ----

    private static UiSnapshot snap(UiBuilder.NodeBuilder<?> ui) {
        return UiSnapshot.capture(ui);
    }

    @Test
    void patchOfAnIdTheClientDidNotBuildAsATableIsIgnored() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 1, true, snap(UiBuilder.table().add(UiBuilder.label("Body").id("text"))));

        client.update(MENU, "missing", snap(UiBuilder.table().add(UiBuilder.label("Lost"))));
        client.update(MENU, "text", snap(UiBuilder.table().add(UiBuilder.label("Also lost"))));

        assertNull(client.lastPatchDsl("missing"));
        assertNull(client.lastPatchDsl("text"));
        assertEquals(java.util.List.of("missing", "text"), client.ignoredPatches());
    }

    @Test
    void patchReplacesWhatTheTableHeld() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 1, true, snap(UiBuilder.table()
                .add(UiBuilder.table().id("outer").add(UiBuilder.table().id("inner")))));

        client.update(MENU, "outer", snap(UiBuilder.table().add(UiBuilder.label("New"))));

        assertTrue(client.isShown(MENU, "outer"));
        assertFalse(client.isShown(MENU, "inner"), "the table the patch cleared is off the screen");
        // The client still has the cleared table in its id map: a patch of it lands where no one sees it.
        client.update(MENU, "inner", snap(UiBuilder.table().add(UiBuilder.label("Unseen"))));
        assertFalse(client.isShown(MENU, "inner"));
    }

    @Test
    void pressSendsWithAPatchsButtonOnlyThatPatchsFields() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 1, true, snap(UiBuilder.table()
                .add(UiBuilder.field("dialog text").id("f_dialog"))
                .add(UiBuilder.table().id("slot"))
                .add(UiBuilder.button("InDialog").clicked("dialog"))));
        client.update(MENU, "slot", snap(UiBuilder.table()
                .add(UiBuilder.field("patch text").id("f_patch"))
                .add(UiBuilder.button("InPatch").clicked("patch"))));

        client.press(MENU, "patch", java.util.Map.of("f_patch", "typed"));
        var fromPatch = client.outbox().remove();
        assertEquals(java.util.Map.of("f_patch", "typed"), fromPatch.values());

        client.press(MENU, "dialog");
        var fromDialog = client.outbox().remove();
        assertEquals(java.util.Map.of("f_dialog", "dialog text", "f_patch", "patch text"), fromDialog.values());
    }

    @Test
    void pressRejectsAFieldThePressedButtonsBuildDoesNotSend() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 1, true, snap(UiBuilder.table()
                .add(UiBuilder.field("").id("f_dialog"))
                .add(UiBuilder.table().id("slot"))));
        client.update(MENU, "slot", snap(UiBuilder.table().add(UiBuilder.button("Save").clicked("save"))));

        var error = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> client.press(MENU, "save", java.util.Map.of("f_dialog", "typed")));
        assertTrue(error.getMessage().contains("f_dialog"));
        assertTrue(client.outbox().isEmpty());
    }

    @Test
    void pressSendsChecksAndSlidersWithTheirInitialValues() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 1, true, snap(UiBuilder.table()
                .add(UiBuilder.check("Chat").id("c").checked(true))
                .add(UiBuilder.slider(5f, 10f, 1f).id("s"))
                .add(UiBuilder.slider(0f, 10f, 1f).id("d").defaultValue(7f))
                .add(UiBuilder.button("Go").clicked("go"))));

        client.press(MENU, "go");

        assertEquals(java.util.Map.of("c", true, "s", 5f, "d", 7f), client.outbox().remove().values());
    }

    @Test
    void pressFailsForWhatTheScreenDoesNotShow() {
        var client = new HeadlessMenuClient(ClientScreen.PHONE_PORTRAIT);
        client.show(MENU, 1, true, snap(UiBuilder.table()
                .add(UiBuilder.button("Wide").clicked("wide").condition("width >= 800"))
                .add(UiBuilder.button("Narrow").clicked("narrow").condition("width < 800"))
                .add(UiBuilder.button("Off").clicked("off").disabled(true))));

        client.press(MENU, "narrow");
        assertEquals("narrow", client.outbox().remove().action());

        var missing = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> client.press(MENU, "wide"));
        assertTrue(missing.getMessage().contains("narrow"), missing.getMessage());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> client.press(MENU, "off"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> client.press(MENU, "removed"));
    }

    @Test
    void hideOnClickClosesWithoutReportingAClose() {
        var client = new HeadlessMenuClient();
        client.show(MENU, 3, true, true, snap(UiBuilder.table().add(UiBuilder.button("Go").clicked("go"))));

        client.press(MENU, "go");

        assertEquals("go", client.outbox().remove().action());
        assertFalse(client.isVisible(MENU));
        client.hide(MENU);
        assertTrue(client.outbox().isEmpty());
    }

    @Test
    void screenDecidesConditionsAsTheClientDoes() {
        assertTrue(ClientScreen.DESKTOP.matches("landscape"));
        assertTrue(ClientScreen.PHONE_PORTRAIT.matches("portrait"));
        assertTrue(ClientScreen.DESKTOP.matches("width >= 800"));
        assertFalse(ClientScreen.PHONE_PORTRAIT.matches("width >= 800"));
        assertTrue(ClientScreen.PHONE_PORTRAIT.matches("height > 800"));
        assertTrue(ClientScreen.DESKTOP.matches("width ~ 3"), "an operator it cannot read counts as true");
        assertTrue(ClientScreen.DESKTOP.matches("width >= lots"), "a number it cannot read counts as true");
    }
}
