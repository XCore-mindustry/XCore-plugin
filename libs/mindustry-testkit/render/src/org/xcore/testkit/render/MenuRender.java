package org.xcore.testkit.render;

import arc.Core;
import arc.Events;
import arc.Settings;
import arc.backend.sdl.SdlApplication;
import arc.backend.sdl.SdlConfig;
import arc.files.Fi;
import arc.scene.Element;
import arc.scene.ui.Dialog;
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
import mindustry.ui.Menus;
import mindustry.ui.builder.UiBuilder;

import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Opens server menus in the real desktop client and checks how it lays them out.
 *
 * <p>One process is one client started with one {@link Profile}; it opens every input in turn,
 * the way a server would send them, waits for the layout to settle, saves a screenshot, the
 * element tree and a JSON report, closes the dialog and goes on to the next. An input is a
 * {@code .packet} (a {@link MenuBuilderCallPacket} as the server writes it) or a {@code .msui}
 * (the menu DSL, opened as a full-screen dialog).
 *
 * <pre>
 * MenuRender --profile phone --out results a.packet b.msui ...
 * </pre>
 *
 * Exit status: 0 when no input has findings, 1 when some have, 2 when an input could not be
 * opened or never settled, or the client did not start with the profile it was asked for.
 *
 * <p>Compiled against the official desktop release rather than shipped in any plugin.
 */
public final class MenuRender {

    /** Frames before the layout is trusted to have settled at all. */
    private static final int MIN_FRAMES = 30;
    /** Frames in a row with unchanged geometry that count as settled. */
    private static final int STABLE_FRAMES = 8;
    /** How long one input may take to open, settle and close. */
    private static final long INPUT_TIMEOUT_NANOS = 20_000_000_000L;

    private enum Phase { OPEN, SETTLE, CLOSE }

    private final Profile profile;
    private final Path out;
    private final Deque<Path> inputs;
    private Phase phase = Phase.OPEN;
    private Path input;
    private int menuId;
    private Dialog dialog;
    private int frames;
    private int stable;
    private String previous = "";
    private long inputStarted;
    private boolean anyFindings;
    private boolean anyFailure;

    private MenuRender(Profile profile, Path out, List<Path> inputs) {
        this.profile = profile;
        this.out = out;
        this.inputs = new ArrayDeque<>(inputs);
    }

    public static void main(String[] args) {
        int status;
        try {
            status = run(args);
        } catch (Throwable failure) {
            // Left to the JVM, a failure to start would exit with 1, which reads as findings.
            failure.printStackTrace();
            status = 2;
        }
        System.exit(status);
    }

    private static int run(String[] args) throws Exception {
        Profile profile = null;
        Path out = null;
        List<Path> inputs = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--profile" -> profile = Profile.of(args[++i]);
                case "--out" -> out = Path.of(args[++i]).toAbsolutePath();
                default -> inputs.add(Path.of(args[i]));
            }
        }
        if (profile == null || out == null || inputs.isEmpty()) {
            System.err.println("Usage: MenuRender --profile <" + Profile.ids() + "> --out <dir> <input.packet|input.msui>...");
            return 2;
        }
        // Results are named after the input without its extension, so two such inputs would overwrite each other.
        Set<String> stems = new HashSet<>();
        for (Path input : inputs) {
            if (!stems.add(stem(input))) {
                System.err.println("Two inputs are named " + stem(input));
                return 2;
            }
        }
        Files.createDirectories(out);
        return new MenuRender(profile, out, inputs).start();
    }

    private int start() {
        String data = System.getProperty("java.io.tmpdir") + "/mindustry-render-" + ProcessHandle.current().pid();
        System.setProperty("nodiscord", "true");
        System.setProperty("mindustry.data.dir", data);
        Vars.testMobile = profile.mobile;
        // Scaling has to be set before the fonts load, so it is written into fresh settings first.
        Settings seed = new Settings();
        seed.setDataDirectory(new Fi(data));
        seed.load();
        seed.put("uiscale", Math.round(profile.scale * 100));
        seed.put("locale", "en");
        seed.put("fpscap", 60);
        seed.put("vsync", false);
        seed.put("bloom", false);
        seed.put("firsttimeshown", true);
        seed.put("crashreport", false);
        seed.forceSave();
        Version.init();

        Events.on(ClientLoadEvent.class, event -> Core.app.post(() -> guard(() -> {
            if (Core.graphics.getWidth() != profile.width || Core.graphics.getHeight() != profile.height
                    || Math.abs(Scl.scl() - profile.scale) > 0.001f) {
                throw new IllegalStateException("The client did not start with profile " + profile.id());
            }
            // A menu dialog closes itself without an active connection; client and server stay false,
            // so no socket is opened and nothing is sent.
            Vars.net = new Net(Vars.platform.getNet()) {
                @Override
                public boolean active() {
                    return true;
                }
            };
            Events.run(Trigger.uiDrawEnd, () -> guard(this::frame));
        })));

        SdlConfig config = new SdlConfig();
        config.width = profile.width;
        config.height = profile.height;
        config.maximized = false;
        config.resizable = false;
        config.disableAudio = true;
        config.vSyncEnabled = false;
        config.title = "Menu render";
        new SdlApplication(new DesktopLauncher(new String[0]), config);
        return anyFailure ? 2 : anyFindings ? 1 : 0;
    }

    /** One drawn frame: opens the next input, waits for it to settle or to close. */
    private void frame() throws Exception {
        if (phase != Phase.OPEN && System.nanoTime() - inputStarted > INPUT_TIMEOUT_NANOS) {
            if (phase == Phase.SETTLE) {
                fail(input, "did not settle in time");
                // Closing gets a timeout of its own.
                inputStarted = System.nanoTime();
                closeAndContinue();
            } else {
                fail(input, "did not close in time");
                // Taken off the scene by hand, so the next input opens on its own.
                dialog.remove();
                next();
            }
            return;
        }
        switch (phase) {
            case OPEN -> open();
            case SETTLE -> settle();
            case CLOSE -> {
                if (dialog.getScene() == null) {
                    next();
                }
            }
        }
    }

    private void open() {
        if (inputs.isEmpty()) {
            Core.app.exit();
            return;
        }
        input = inputs.poll();
        inputStarted = System.nanoTime();
        frames = 0;
        stable = 0;
        previous = "";
        dialog = null;
        try {
            MenuBuilderCallPacket packet = read(input);
            menuId = packet.id;
            packet.handleClient();
        } catch (Exception broken) {
            fail(input, "could not be read or opened: " + broken);
            next();
            return;
        }
        for (Element child : Core.scene.root.getChildren()) {
            if (child.getClass().getName().equals("mindustry.ui.Menus$MenuDialog")) dialog = (Dialog) child;
        }
        if (dialog == null) {
            fail(input, "did not open a menu dialog");
            next();
            return;
        }
        phase = Phase.SETTLE;
    }

    private void settle() throws Exception {
        dialog.validate();
        String current = Inspector.geometry(dialog);
        stable = current.equals(previous) ? stable + 1 : 0;
        previous = current;
        frames++;
        if (frames < MIN_FRAMES || stable < STABLE_FRAMES || dialog.hasActions()) return;

        Inspector inspector = new Inspector(dialog).run();
        String stem = stem(input) + "-" + profile.id();
        ScreenUtils.saveScreenshot(new Fi(out.resolve(stem + ".png").toFile()));
        Files.write(out.resolve(stem + ".tree.txt"), inspector.tree);
        Files.writeString(out.resolve(stem + ".json"), report(stem, inspector.findings));
        anyFindings |= !inspector.findings.isEmpty();
        System.out.println(stem + " findings=" + inspector.findings.size());
        for (Inspector.Finding finding : inspector.findings) {
            System.out.println("  " + finding.code() + " " + finding.element());
        }
        closeAndContinue();
    }

    private void closeAndContinue() {
        if (dialog != null && dialog.getScene() != null) {
            Menus.hideMenuBuilder(menuId);
            phase = Phase.CLOSE;
        } else {
            next();
        }
    }

    private void next() {
        dialog = null;
        phase = Phase.OPEN;
    }

    private MenuBuilderCallPacket read(Path path) throws Exception {
        MenuBuilderCallPacket packet = new MenuBuilderCallPacket();
        if (path.toString().endsWith(".msui")) {
            packet.id = 1;
            packet.hidePrevious = true;
            packet.fillScreen = true;
            packet.builder = UiBuilder.parse(Files.readString(path));
            return packet;
        }
        try (var stream = new DataInputStream(Files.newInputStream(path))) {
            packet.read(new Reads(stream), Math.toIntExact(Files.size(path)));
        }
        packet.handled();
        return packet;
    }

    private String report(String stem, List<Inspector.Finding> findings) {
        StringBuilder json = new StringBuilder("{\n");
        json.append("  \"capture\": ").append(quote(stem)).append(",\n");
        json.append("  \"input\": ").append(quote(input.getFileName().toString())).append(",\n");
        json.append("  \"profile\": ").append(quote(profile.id())).append(",\n");
        json.append("  \"client\": ").append(quote(Version.buildString())).append(",\n");
        json.append(String.format(Locale.ROOT,
                "  \"framebuffer\": [%d, %d],\n  \"scene\": [%.0f, %.0f],\n  \"scale\": %.2f,\n  \"mobile\": %s,\n",
                Core.graphics.getWidth(), Core.graphics.getHeight(), Core.scene.getWidth(), Core.scene.getHeight(),
                Scl.scl(), Vars.mobile));
        json.append(String.format(Locale.ROOT, "  \"frames\": %d,\n  \"elapsedMs\": %.0f,\n",
                frames, (System.nanoTime() - inputStarted) / 1e6));
        json.append("  \"findings\": [");
        for (int i = 0; i < findings.size(); i++) {
            Inspector.Finding finding = findings.get(i);
            json.append(i == 0 ? "\n" : ",\n").append("    {\"code\": ").append(quote(finding.code()))
                    .append(", \"element\": ").append(quote(finding.element())).append('}');
        }
        json.append(findings.isEmpty() ? "]\n" : "\n  ]\n").append("}\n");
        return json.toString();
    }

    private static String quote(String text) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                default -> {
                    if (c < 0x20) quoted.append(String.format("\\u%04x", (int) c));
                    else quoted.append(c);
                }
            }
        }
        return quoted.append('"').toString();
    }

    private static String stem(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private void fail(Path path, String why) {
        anyFailure = true;
        System.out.println(stem(path) + "-" + profile.id() + " FAILED: " + why);
    }

    /** A failure of the harness itself ends the run: nothing after it can be trusted. */
    private static void guard(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(2);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
