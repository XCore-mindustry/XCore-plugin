import arc.Core;
import arc.Events;
import arc.Settings;
import arc.backend.sdl.SdlApplication;
import arc.backend.sdl.SdlConfig;
import arc.files.Fi;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Scl;
import arc.util.ScreenUtils;
import arc.util.io.Reads;
import mindustry.Vars;
import mindustry.core.Version;
import mindustry.desktop.DesktopLauncher;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.Trigger;
import mindustry.gen.MenuBuilderCallPacket;
import mindustry.net.Net;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Standalone test process, compiled against the official desktop jar rather than shipped in the plugin. */
public class Render {
    private static final float EPS = 1.5f;
    private static final List<String> findings = new ArrayList<>(), tree = new ArrayList<>();
    private static final long started = System.nanoTime();
    private static Path output;
    private static Dialog dialog;
    private static int frames, stable, exitCode = 2;
    private static String previous = "";
    private static boolean done;

    private static String geometry(Element element) {
        StringBuilder value = new StringBuilder().append(element.x).append(',').append(element.y)
                .append(',').append(element.getWidth()).append(',').append(element.getHeight());
        if (element instanceof Group group) {
            for (Element child : group.getChildren()) value.append('|').append(geometry(child));
        }
        return value.toString();
    }

    private static void inspect(Element element, boolean verticalScroll, int depth) {
        if (!element.visible) return;
        Vec2 position = element.localToStageCoordinates(new Vec2());
        String text = element instanceof Label label ? label.getText().toString().replace('\n', ' ') : "";
        String id = element.getClass().getSimpleName() + "[" + element.name + "] "
                + text.substring(0, Math.min(100, text.length()));
        tree.add(" ".repeat(depth) + id + String.format(Locale.ROOT,
                " xy=%.1f,%.1f wh=%.1f,%.1f pref=%.1f,%.1f", position.x, position.y,
                element.getWidth(), element.getHeight(), element.getPrefWidth(), element.getPrefHeight()));
        Vec2 origin = dialog.localToStageCoordinates(new Vec2());
        if (position.x < -EPS || position.x + element.getWidth() > Core.scene.getWidth() + EPS)
            findings.add("STAGE_X " + id);
        if (!verticalScroll && (position.y < -EPS || position.y + element.getHeight() > Core.scene.getHeight() + EPS))
            findings.add("STAGE_Y " + id);
        if (position.x < origin.x - EPS || position.x + element.getWidth() > origin.x + dialog.getWidth() + EPS)
            findings.add("DIALOG_X " + id);
        if (!verticalScroll && (position.y < origin.y - EPS || position.y + element.getHeight() > origin.y + dialog.getHeight() + EPS))
            findings.add("DIALOG_Y " + id);
        if (element instanceof ScrollPane pane) {
            if (pane.isScrollX() && pane.getMaxX() > EPS) findings.add("HSCROLL " + id);
            if (pane.getWidget() != null && pane.getWidget().getWidth() > pane.getScrollWidth() + EPS)
                findings.add("PANE_CONTENT_X " + id);
        }
        if (element instanceof Label label) {
            var glyphs = label.getGlyphLayout();
            if (glyphs.width > label.getWidth() + EPS) findings.add("TEXT_X " + id);
            if (glyphs.height > label.getHeight() + EPS) findings.add("TEXT_Y " + id);
            // Labels can fit their assigned size yet extend beyond the button that owns them.
            for (Element parent = label.parent; parent != null; parent = parent.parent) {
                if (parent instanceof Button) {
                    Vec2 relative = label.localToAscendantCoordinates(parent, new Vec2());
                    if (relative.x < -EPS || relative.x + label.getWidth() > parent.getWidth() + EPS
                            || relative.y < -EPS || relative.y + label.getHeight() > parent.getHeight() + EPS)
                        findings.add("BUTTON_TEXT " + id);
                    break;
                }
            }
        }
        if (element instanceof Group group) {
            boolean scroll = verticalScroll || element instanceof ScrollPane pane && pane.isScrollY();
            for (Element child : group.getChildren()) inspect(child, scroll, depth + 1);
        }
    }

    private static void capture() {
        if (done) return;
        dialog.validate();
        String current = geometry(dialog);
        stable = current.equals(previous) ? stable + 1 : 0;
        previous = current;
        frames++;
        if (frames < 30 || stable < 8 || dialog.hasActions()) return;
        done = true;
        try {
            inspect(dialog, false, 0);
            ScreenUtils.saveScreenshot(new Fi(output + ".png"));
            Files.write(Path.of(output + ".tree.txt"), tree);
            Files.write(Path.of(output + ".checks.txt"), findings);
            String metrics = String.format(Locale.ROOT,
                    "client=%s framebuffer=%dx%d scene=%.0fx%.0f scale=%.2f mobile=%s frames=%d findings=%d elapsedMs=%.0f%n",
                    Version.buildString(), Core.graphics.getWidth(), Core.graphics.getHeight(),
                    Core.scene.getWidth(), Core.scene.getHeight(), Scl.scl(), Vars.mobile, frames,
                    findings.size(), (System.nanoTime() - started) / 1e6);
            Files.writeString(Path.of(output + ".metrics.txt"), metrics);
            System.out.print(metrics);
            exitCode = findings.isEmpty() ? 0 : 1;
            Core.app.exit();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(2);
        }
    }

    public static void main(String[] args) throws Exception {
        // packet, output stem, framebuffer width/height, UI scale, mobile
        Path packetPath = Path.of(args[0]);
        output = Path.of(args[1]).toAbsolutePath();
        Files.createDirectories(output.getParent());
        int width = Integer.parseInt(args[2]), height = Integer.parseInt(args[3]);
        float scale = Float.parseFloat(args[4]);
        System.setProperty("nodiscord", "true");
        System.setProperty("mindustry.data.dir", "/tmp/mindustry-render-data");
        Vars.testMobile = Boolean.parseBoolean(args[5]);
        Settings seed = new Settings();
        seed.setDataDirectory(new Fi("/tmp/mindustry-render-data"));
        seed.load();
        seed.put("uiscale", Math.round(scale * 100));
        seed.put("locale", "en");
        seed.put("fpscap", 60);
        seed.put("vsync", false);
        seed.put("bloom", false);
        seed.put("firsttimeshown", true);
        seed.put("crashreport", false);
        seed.forceSave();
        Version.init();
        Events.on(ClientLoadEvent.class, event -> Core.app.post(() -> {
            try {
                if (Core.graphics.getWidth() != width || Core.graphics.getHeight() != height
                        || Math.abs(Scl.scl() - scale) > 0.001f)
                    throw new AssertionError("Framebuffer or UI scale differs from the requested profile");
                // MenuDialog needs an active connection; client/server stay false, so no socket or timeout is used.
                Vars.net = new Net(Vars.platform.getNet()) {
                    @Override public boolean active() { return true; }
                };
                MenuBuilderCallPacket packet = new MenuBuilderCallPacket();
                try (var input = new DataInputStream(Files.newInputStream(packetPath))) {
                    packet.read(new Reads(input), Math.toIntExact(Files.size(packetPath)));
                }
                packet.handled();
                packet.handleClient();
                for (Element child : Core.scene.root.getChildren()) {
                    if (child.getClass().getName().equals("mindustry.ui.Menus$MenuDialog")) dialog = (Dialog) child;
                }
                if (dialog == null) throw new AssertionError("Packet did not open a MenuDialog");
                Events.run(Trigger.uiDrawEnd, Render::capture);
            } catch (Throwable failure) {
                failure.printStackTrace();
                System.exit(2);
            }
        }));
        SdlConfig config = new SdlConfig();
        config.width = width;
        config.height = height;
        config.maximized = false;
        config.resizable = false;
        config.disableAudio = true;
        config.vSyncEnabled = false;
        config.title = "XCore menu render";
        new SdlApplication(new DesktopLauncher(new String[0]), config);
        System.exit(exitCode);
    }
}
