package org.xcore.testkit.ui;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.Pixmap;
import arc.graphics.Texture;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.TextureRegion;
import arc.mock.MockApplication;
import arc.mock.MockGL20;
import arc.mock.MockGraphics;
import arc.scene.Scene;
import arc.scene.ui.Dialog;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Bounded feasibility spike for an in-JVM actual-client oracle.
 * Single behavior under test: Dialog.hide(null) fires the hidden listener
 * synchronously on the calling thread (recorded client semantics the fake
 * must reproduce).
 */
class ActualDialogHideTest {
    @Test
    void hideFiresHiddenListenerSynchronouslyOnCallingThread() {
        Core.gl = new MockGL20();
        Core.graphics = new MockGraphics();
        Core.app = new MockApplication();
        Core.scene = new Scene();
        Dialog.DialogStyle dialogStyle = new Dialog.DialogStyle();
        dialogStyle.titleFont = new Font(new Font.FontData(), new TextureRegion(new Texture(new Pixmap(16, 16))), true);
        Core.scene.addStyle(Dialog.DialogStyle.class, dialogStyle);

        Dialog dialog = new Dialog();

        AtomicInteger hiddenCalls = new AtomicInteger();
        dialog.hidden(hiddenCalls::incrementAndGet);

        dialog.show(Core.scene);
        dialog.hide(null);

        assertFalse(dialog.isShown());
        assertEquals(1, hiddenCalls.get());
    }
}
