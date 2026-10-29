package tilelogger;

import arc.util.OS;
import org.xcore.plugin.concurrent.GameThread;

import java.io.InputStream;
import java.nio.file.Files;

/** The native store is game-thread owned; only archive preparation and flush may block. */
public final class TileLogger {
    public static final int ABI = 2;
    private static Throwable loadFailure;
    private static volatile boolean live;
    public static void setLive(boolean value) {
        live = value;
    }
    public static boolean isLive() {
        return live;
    }
    static {
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
                System.load(temporary.toAbsolutePath().toString());
            }
            if (abi() != ABI)
                throw new IllegalStateException("TileLogger native ABI mismatch");
        } catch (Exception | LinkageError failure) {
            loadFailure = failure;
        }
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
        start(path, session, manifest);
        live = true;
    }
    public static long prepareArchive(String path, String manifest) {
        ensureAvailable();
        if (arc.Core.app != null && GameThread.isGameThread())
            throw new IllegalStateException("Archive loading requires a worker");
        return prepare(path, manifest);
    }
    public static void activateArchive(long handle) {
        mainThread();
        activate(handle);
        live = false;
    }
    public static void discardArchive(long handle) {
        ensureAvailable();
        discard(handle);
    }
    public static long duration() {
        mainThread();
        return elapsed();
    }
    public static void record(int x, int y, String uuid, int team, int block, int rotation,
            ConfigCodec.Encoded config, ActionKind kind) {
        mainThread();
        action(x, y, uuid, team, block, rotation, config.type(), config.scalar(), config.bytes(), kind.id);
    }
    public static TileState[] getHistory(
            int x1, int y1, int x2, int y2, String uuid, int teams, long time, long size) {
        return getHistory(x1, y1, x2, y2, uuid, teams, time, size, Long.MAX_VALUE);
    }
    public static TileState[] getHistory(
            int x1, int y1, int x2, int y2, String uuid, int teams, long time, long size, long before) {
        mainThread();
        return history(x1, y1, x2, y2, uuid, teams, time, size, before);
    }
    public static TileState[] planRollback(Rect rect, String uuid, int teams, long time) {
        mainThread();
        return plan(rect.x1, rect.y1, rect.x2, rect.y2, uuid, teams, time);
    }
    public static void commitRollback(TileState change) {
        mainThread();
        commit(change.planId, change.x, change.y);
    }
    public static TileState currentState(int x, int y) {
        mainThread();
        TileState[] states = current(x, y);
        return states.length == 0 ? null : states[0];
    }
    public static void discardPlan(long id) {
        mainThread();
        forget(id);
    }
    public static long memoryUsage(long id) {
        mainThread();
        return memory(Math.toIntExact(id));
    }
    public static boolean isReadOnly() {
        mainThread();
        return readOnly();
    }
    public static String sessionId() {
        mainThread();
        return session();
    }
    public static String status() {
        mainThread();
        return health();
    }
    public static void checkWritable() {
        mainThread();
        writable();
    }
    public static String getBuildString() {
        ensureAvailable();
        return build();
    }
    /** Shutdown/tests only, never a game tick. */
    public static void flush() {
        ensureAvailable();
        flushWrites();
    }

    private static native int abi();
    private static native void start(String path, String session, String manifest);
    private static native long prepare(String path, String manifest);
    private static native void activate(long handle);
    private static native void discard(long handle);
    private static native long elapsed();
    private static native void action(int x, int y, String uuid, int team, int block, int rotation, int type,
            int scalar, byte[] payload, int kind);
    private static native TileState[] history(
            int x1, int y1, int x2, int y2, String uuid, int teams, long time, long size, long before);
    private static native TileState[] plan(int x1, int y1, int x2, int y2, String uuid, int teams, long time);
    private static native void commit(long plan, int x, int y);
    private static native void forget(long plan);
    private static native TileState[] current(int x, int y);
    private static native long memory(int id);
    private static native boolean readOnly();
    private static native String session();
    private static native String health();
    private static native void writable();
    private static native String build();
    private static native void flushWrites();
}
