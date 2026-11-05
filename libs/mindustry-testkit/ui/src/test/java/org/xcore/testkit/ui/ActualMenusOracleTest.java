package org.xcore.testkit.ui;

import arc.Core;
import arc.audio.Sound;
import arc.func.Cons;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.TextureRegion;
import arc.mock.MockApplication;
import arc.mock.MockAudio;
import arc.mock.MockGL20;
import arc.mock.MockGraphics;
import arc.scene.Scene;
import arc.scene.style.BaseDrawable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import mindustry.Vars;
import mindustry.gen.Call;
import mindustry.gen.Icon;
import mindustry.gen.MenuBuilderChooseCallPacket;
import mindustry.gen.Sounds;
import mindustry.gen.Tex;
import mindustry.net.Host;
import mindustry.net.Net;
import mindustry.net.NetConnection;
import mindustry.ui.Menus;
import mindustry.ui.builder.UiBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Actual-client oracle: executes the REAL mindustry.ui.Menus against a Mock Arc
 * environment, proving that HeadlessMenuClient reproduces actual client semantics.
 */
class ActualMenusOracleTest {
    private static final int MENU = 7;
    private final Queue<MenuBuilderChooseCallPacket> outbox = new ArrayDeque<>();

    @BeforeEach
    void setUp() {
        Core.gl = new MockGL20();
        Core.graphics = new MockGraphics();
        Core.app = new MockApplication();
        Core.audio = new MockAudio();
        Core.scene = new Scene();

        Font font = new Font(new Font.FontData(), new TextureRegion(new Texture(new Pixmap(16, 16))), true);

        Dialog.DialogStyle dialogStyle = new Dialog.DialogStyle();
        dialogStyle.titleFont = font;
        Core.scene.addStyle(Dialog.DialogStyle.class, dialogStyle);

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        labelStyle.fontColor = Color.white;
        Core.scene.addStyle(Label.LabelStyle.class, labelStyle);

        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.font = font;
        Core.scene.addStyle(Button.ButtonStyle.class, textButtonStyle);
        Core.scene.addStyle(TextButton.TextButtonStyle.class, textButtonStyle);

        arc.scene.ui.TextField.TextFieldStyle fieldStyle = new arc.scene.ui.TextField.TextFieldStyle();
        fieldStyle.font = font;
        fieldStyle.fontColor = Color.white;
        Core.scene.addStyle(arc.scene.ui.TextField.TextFieldStyle.class, fieldStyle);

        Tex.whiteui = new BaseDrawable();
        Texture dummyTexture = new Texture(new Pixmap(16, 16));
        Icon.left = new TextureRegionDrawable(new TextureRegion(dummyTexture));
        Sounds.uiBack = new Sound();

        Vars.net = new Net(new Net.NetProvider() {
            @Override public void connectClient(String ip, int port, Runnable success) {}
            @Override public void sendClient(Object object, boolean reliable) {
                if (object instanceof MenuBuilderChooseCallPacket packet) {
                    outbox.add(packet);
                }
            }
            @Override public void disconnectClient() {}
            @Override public void discoverServers(Cons<Host> callback, Runnable done) {}
            @Override public void pingHost(String address, int port, Cons<Host> valid, Cons<Exception> failed) {}
            @Override public void hostServer(int port) {}
            @Override public Iterable<? extends NetConnection> getConnections() { return java.util.Collections.emptyList(); }
            @Override public void closeServer() {}
        });
        Vars.net.setClientConnected();
    }

    @AfterEach
    void tearDown() {
        Menus.hideMenuBuilder(MENU);
        outbox.clear();
    }

    @Test
    void realMenusReplacementEmitsOldWindowCancelWithOldToken() {
        var body1 = UiBuilder.table().add(UiBuilder.label("First"));
        Menus.menuBuilder(MENU, 101L, null, true, true, false, body1);

        assertTrue(outbox.isEmpty());

        // Replace the dialog with a new one; hidePrevious=true
        var body2 = UiBuilder.table().add(UiBuilder.label("Second"));
        Menus.menuBuilder(MENU, 202L, null, true, true, false, body2);

        // The real Menus.menuBuilder hid oldDialog with hide(null), which synchronously
        // triggered oldDialog.hidden(), emitting Call.menuBuilderChoose with token=101 and cancelled=true!
        assertEquals(1, outbox.size());
        var packet = outbox.poll();
        assertNotNull(packet);
        assertEquals(MENU, packet.menuId);
        assertNotNull(packet.results);
        assertEquals(101L, packet.results.token);
        assertTrue(packet.results.wasCancelled());
        assertNull(packet.results.result);

        // Dismissing the second window emits cancel with the NEW token (202)
        Menus.hideMenuBuilder(MENU);
        assertEquals(1, outbox.size());
        var packet2 = outbox.poll();
        assertNotNull(packet2);
        assertEquals(202L, packet2.results.token);
        assertTrue(packet2.results.wasCancelled());
    }

    @Test
    void realMenusReplacementAfterClickSuppressesOldWindowCancel() {
        var body1 = UiBuilder.table().add(UiBuilder.button("SelectMap").clicked("action:select_map:arena"));
        // hideOnClick=false so the dialog stays open after clicking, awaiting replacement
        Menus.menuBuilder(MENU, 101L, null, false, true, false, body1);

        assertTrue(outbox.isEmpty());

        // Find the button in Core.scene and click it
        var button = findButton(Core.scene.root, "SelectMap");
        assertNotNull(button, "button must be found in scene");
        button.fireClick();

        // Button click emitted choose packet with action and token=101
        assertEquals(1, outbox.size());
        var clickPacket = outbox.poll();
        assertNotNull(clickPacket);
        assertEquals(101L, clickPacket.results.token);
        assertEquals("action:select_map:arena", clickPacket.results.result);
        assertFalse(clickPacket.results.wasCancelled());

        // Replace with second window; hidePrevious=true
        var body2 = UiBuilder.table().add(UiBuilder.label("Second"));
        Menus.menuBuilder(MENU, 202L, null, true, true, false, body2);

        // Old window wasHidden == true -> hidden listener suppressed cancellation!
        // Outbox must be EMPTY!
        assertTrue(outbox.isEmpty(), "replacement after click must NOT emit cancel from old window");

        // Dismissing the second window emits cancel with token 202
        Menus.hideMenuBuilder(MENU);
        assertEquals(1, outbox.size());
        var cancelPacket = outbox.poll();
        assertNotNull(cancelPacket);
        assertEquals(202L, cancelPacket.results.token);
        assertTrue(cancelPacket.results.wasCancelled());
    }

    @Test
    void realMenusIgnoresAPatchOfAnIdItDidNotBuildAsATable() {
        Menus.menuBuilder(MENU, 1L, null, false, true, false,
                UiBuilder.table().add(UiBuilder.label("Body").id("text")));

        Menus.menuBuilderUpdate(MENU, "missing", UiBuilder.table().add(UiBuilder.label("Lost")));
        Menus.menuBuilderUpdate(MENU, "text", UiBuilder.table().add(UiBuilder.label("Also lost")));

        assertNull(findLabel(Core.scene.root, "Lost"), "a patch of an unknown id builds nothing");
        assertNull(findLabel(Core.scene.root, "Also lost"), "a patch of a label builds nothing");
        assertNotNull(findLabel(Core.scene.root, "Body"));
        assertTrue(outbox.isEmpty());
    }

    @Test
    void realMenusPatchReplacesWhatTheTableHeld() {
        Menus.menuBuilder(MENU, 1L, null, false, true, false,
                UiBuilder.table().add(UiBuilder.table().id("slot").add(UiBuilder.label("Old"))));

        Menus.menuBuilderUpdate(MENU, "slot", UiBuilder.table().add(UiBuilder.label("New")));

        assertNull(findLabel(Core.scene.root, "Old"));
        assertNotNull(findLabel(Core.scene.root, "New"));
    }

    @Test
    void realMenusSendsWithAPatchsButtonOnlyThatPatchsFields() {
        Menus.menuBuilder(MENU, 1L, null, false, true, false, UiBuilder.table()
                .add(UiBuilder.field("typed in the dialog").id("f_dialog"))
                .add(UiBuilder.table().id("slot"))
                .add(UiBuilder.button("InDialog").clicked("dialog")));
        Menus.menuBuilderUpdate(MENU, "slot", UiBuilder.table()
                .add(UiBuilder.field("typed in the patch").id("f_patch"))
                .add(UiBuilder.button("InPatch").clicked("patch")));
        // The mock font has no glyphs, and a field keeps only what its font can draw; a player's
        // font has them all, so the text goes back in as typed.
        for (var field : findFields(Core.scene.root)) {
            field.setOnlyFontChars(false);
        }
        typeInto("f_dialog", "typed in the dialog");
        typeInto("f_patch", "typed in the patch");

        findButton(Core.scene.root, "InPatch").fireClick();
        var fromPatch = outbox.poll();
        assertNotNull(fromPatch);
        assertEquals("patch", fromPatch.results.result);
        assertEquals("typed in the patch", fromPatch.results.values.get("f_patch"));
        assertFalse(fromPatch.results.values.containsKey("f_dialog"), "a field of the dialog's build is not sent");

        // The dialog's build shares its id map with the dialog, which every patch adds its ids to:
        // a button of the dialog sends the patches' fields as well.
        findButton(Core.scene.root, "InDialog").fireClick();
        var fromDialog = outbox.poll();
        assertNotNull(fromDialog);
        assertEquals("typed in the dialog", fromDialog.results.values.get("f_dialog"));
        assertEquals("typed in the patch", fromDialog.results.values.get("f_patch"));
    }

    @Test
    void realMenusFieldKeepsWhatItsLimitAllows() {
        Menus.menuBuilder(MENU, 1L, null, false, true, false, UiBuilder.table()
                .add(UiBuilder.field("").id("short").maxLength(5))
                .add(UiBuilder.field("").id("long"))
                .add(UiBuilder.button("Send").clicked("send")));
        for (var field : findFields(Core.scene.root)) {
            field.setOnlyFontChars(false); // see realMenusSendsWithAPatchsButtonOnlyThatPatchsFields
        }
        typeInto("short", "abc\ndefgh");
        typeInto("long", "x".repeat(1200));

        findButton(Core.scene.root, "Send").fireClick();
        var sent = outbox.poll();
        assertNotNull(sent);
        assertEquals("abcde", sent.results.values.get("short"), "line breaks dropped, cut at maxLength");
        assertEquals("x".repeat(1000), sent.results.values.get("long"), "a field without maxLength holds 1000");
    }

    @Test
    void realMenusSkipsANodeWhoseConditionFails() {
        Menus.menuBuilder(MENU, 1L, null, false, true, false, UiBuilder.table()
                .add(UiBuilder.button("Never").clicked("never").condition("width < 0"))
                .add(UiBuilder.button("Always").clicked("always").condition("width >= 0")));

        assertNull(findButton(Core.scene.root, "Never"));
        assertNotNull(findButton(Core.scene.root, "Always"));
    }

    @Test
    void realMenusDisablesOnlyWhatATableHolds() {
        Menus.menuBuilder(MENU, 1L, null, false, true, false, UiBuilder.table()
                .add(UiBuilder.button("Celled").clicked("celled").disabled(false).disabled(true))
                .add(UiBuilder.stack().add(UiBuilder.button("Stacked").clicked("stacked").disabled(true))));

        assertTrue(findButton(Core.scene.root, "Celled").isDisabled(), "any true entry disables a cell's button");
        assertFalse(findButton(Core.scene.root, "Stacked").isDisabled(), "a stack's own child is in no cell");
    }

    @Test
    void realMenusHideOnClickClosesWithoutReportingAClose() {
        Menus.menuBuilder(MENU, 3L, null, true, true, false,
                UiBuilder.table().add(UiBuilder.button("Go").clicked("go")));

        findButton(Core.scene.root, "Go").fireClick();
        assertEquals("go", outbox.poll().results.result);
        for (int i = 0; i < 10; i++) Core.scene.act(0.1f); // the dialog's hide action runs out
        assertNull(findButton(Core.scene.root, "Go"), "the pressed dialog leaves the scene");

        Menus.hideMenuBuilder(MENU);
        assertTrue(outbox.isEmpty(), "the pressed dialog reports no close");
    }

    @Test
    void loadedArtifactFingerprintsMatchFingerprintedV160Jars() throws Exception {
        // Exact SHA-256 of the loaded JARs in the runtime classpath to detect cache drift
        assertJarSha256(Menus.class, "283c9b56fb22a01d4fb804618b9dd04ef3dba88a1dac9b1bc04d208628381dc5");
        assertJarSha256(Core.class, "c2df13f7db05e3ae56278250ea75a1abcfd1290e005455bf85d9fea0216303e1");
    }

    private static void assertJarSha256(Class<?> clazz, String expectedHex) throws Exception {
        var location = clazz.getProtectionDomain().getCodeSource().getLocation();
        var digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var is = location.openStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) digest.update(buf, 0, n);
        }
        var actualHex = java.util.HexFormat.of().formatHex(digest.digest());
        assertEquals(expectedHex, actualHex, "SHA-256 fingerprint for " + clazz.getName());
    }

    private static void typeInto(String name, String text) {
        for (var field : findFields(Core.scene.root)) {
            if (name.equals(field.name)) {
                field.setText(text);
                return;
            }
        }
        throw new AssertionError("no field " + name);
    }

    private static java.util.List<arc.scene.ui.TextField> findFields(arc.scene.Group group) {
        var out = new java.util.ArrayList<arc.scene.ui.TextField>();
        for (var child : group.getChildren()) {
            if (child instanceof arc.scene.ui.TextField f) out.add(f);
            if (child instanceof arc.scene.Group g) out.addAll(findFields(g));
        }
        return out;
    }

    private static Label findLabel(arc.scene.Group group, String text) {
        for (var child : group.getChildren()) {
            if (child instanceof Label l && !(child.parent instanceof TextButton) && text.equals(l.getText().toString())) {
                return l;
            }
            if (child instanceof arc.scene.Group g) {
                var found = findLabel(g, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static TextButton findButton(arc.scene.Group group, String text) {
        for (var child : group.getChildren()) {
            if (child instanceof TextButton b && text.equals(b.getText().toString())) {
                return b;
            }
            if (child instanceof arc.scene.Group g) {
                var found = findButton(g, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
