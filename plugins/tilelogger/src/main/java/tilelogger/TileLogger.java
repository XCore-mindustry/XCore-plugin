package tilelogger;

import arc.util.OS;
import arc.util.Log;
import java.nio.file.Files;

public class TileLogger {
    static {
        loadLibrary();
    }

    public static void loadLibrary() {
        try {
            String fileName = OS.isWindows ? "TileLogger.dll" : "libTileLogger.so";
            var file = TileLogger.class.getResource("/" + fileName);

            if (file == null) {
                Log.warn("[TileLogger] Native library not found in resources: " + fileName);
                return;
            }

            var temp = Files.createTempFile("tilelogger", OS.isWindows ? ".dll" : ".so");
            Files.write(temp, file.openStream().readAllBytes());
            temp.toFile().deleteOnExit();

            System.load(temp.toString());
        } catch (Throwable e) {
            Log.err("[TileLogger] Failed to load native library", e);
        }
    }

    public static native long reset(String path, boolean write);
    public static native short duration();
    public static native void onAction(short x, short y, String uuid, short team, short block, short rotation, short config_type, int config);
    public static native void onAction2(short x, short y, String uuid, short team, short block, short rotation, short config_type, byte[] config);
    public static native TileState[] getHistory(short x1, short y1, short x2, short y2, String uuid, int teams, int time, long size);
    public static native TileState[] rollback(short x1, short y1, short x2, short y2, String uuid, int teams, int time, int flags);
    public static native boolean subnetAccepted(String subnet);
    public static native void reloadSubnets();
    public static native long memoryUsage(long id);
    public static native String getBuildString();
}
