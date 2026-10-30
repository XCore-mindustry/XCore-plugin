package tilelogger;

import arc.util.OS;
import org.xcore.plugin.concurrent.GameThread;

import java.io.InputStream;
import java.nio.file.Files;

/** The native store is game-thread owned; only archive preparation and flush may block. */
public final class TileLogger {
    public static final int ABI = 3;
    private static final NativeStore store;
    private static final Throwable loadFailure;
    private static volatile boolean live;
    public static void setLive(boolean value) {
        live = value;
    }
    public static boolean isLive() {
        return live;
    }
    static {
        NativeStore loaded = null;
        Throwable failure = null;
        try {
            String name = OS.isWindows ? "TileLogger.dll"
                    : OS.isMac         ? "libTileLogger.dylib"
                                       : "libTileLogger.so";
            try (InputStream source = TileLogger.class.getResourceAsStream("/" + name)) {
                if (source == null)
                    throw new IllegalStateException("Native library missing: " + name);
                var temporary = Files.createTempFile("tilelogger-", name.substring(name.lastIndexOf('.')));
                temporary.toFile().deleteOnExit();
                Files.copy(source, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                loaded = new NativeStore(temporary.toAbsolutePath());
            }
        } catch (Exception | LinkageError error) {
            failure = error instanceof IllegalCallerException
                    ? new IllegalStateException("Start the server with --enable-native-access=ALL-UNNAMED", error)
                    : error;
        }
        store = loaded;
        loadFailure = failure;
    }
    private TileLogger() {}
    public static void ensureAvailable() {
        if (loadFailure != null)
            throw new IllegalStateException("TileLogger native library unavailable", loadFailure);
    }
    private static void mainThread() {
        ensureAvailable();
        GameThread.require("TileLogger");
    }
    public static void startSession(String path, String session, String manifest) {
        mainThread();
        store.start(path, session, manifest);
        live = true;
    }
    public static long prepareArchive(String path, String manifest) {
        ensureAvailable();
        if (arc.Core.app != null && GameThread.isGameThread())
            throw new IllegalStateException("Archive loading requires a worker");
        return store.prepare(path, manifest);
    }
    public static void activateArchive(long handle) {
        mainThread();
        store.activate(handle);
        live = false;
    }
    public static void discardArchive(long handle) {
        ensureAvailable();
        store.discard(handle);
    }
    public static long duration() {
        mainThread();
        return store.elapsed();
    }
    public static void record(int x, int y, String uuid, int team, int block, int rotation,
            ConfigCodec.Encoded config, ActionKind kind) {
        mainThread();
        store.record(x, y, uuid, team, block, rotation, config, kind);
    }
    public static TileState[] getHistory(
            int x1, int y1, int x2, int y2, String uuid, int teams, long time, long size) {
        return getHistory(x1, y1, x2, y2, uuid, teams, time, size, Long.MAX_VALUE);
    }
    public static TileState[] getHistory(
            int x1, int y1, int x2, int y2, String uuid, int teams, long time, long size, long before) {
        mainThread();
        return store.history(x1, y1, x2, y2, uuid, teams, time, size, before);
    }
    public static TileState[] planRollback(Rect rect, String uuid, int teams, long time) {
        mainThread();
        return store.plan(rect, uuid, teams, time);
    }
    public static void commitRollback(TileState change) {
        mainThread();
        store.commit(change);
    }
    public static TileState currentState(int x, int y) {
        mainThread();
        TileState[] states = store.current(x, y);
        return states.length == 0 ? null : states[0];
    }
    public static void discardPlan(long id) {
        mainThread();
        store.forget(id);
    }
    public static long memoryUsage(long id) {
        mainThread();
        return store.memory(Math.toIntExact(id));
    }
    public static boolean isReadOnly() {
        mainThread();
        return store.readOnly();
    }
    public static String sessionId() {
        mainThread();
        return store.session();
    }
    public static String status() {
        mainThread();
        return store.status();
    }
    public static void checkWritable() {
        mainThread();
        store.writable();
    }
    public static String getBuildString() {
        ensureAvailable();
        return store.build();
    }
    /** Shutdown/tests only, never a game tick. */
    public static void flush() {
        ensureAvailable();
        store.flush();
    }

}
