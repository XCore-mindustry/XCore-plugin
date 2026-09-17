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
