package org.xcore.plugin.service.map;

import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.GameThread;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.service.map.inspect.*;
import org.xcore.protocol.generated.shared.MapFileSourceV1;

import java.io.IOException;

/** Explicit RPC inspection only. Capture on the game thread, then download/read/check entirely on a worker. */
@Singleton
public class MapInspectionService {
    private final TomlSecretsConfig secrets;
    private final MapDownloader downloader;
    private final DetachedMapReadLock readLock;
    private final DetachedMsavReader reader = new DetachedMsavReader();
    private final MapChecks checks = new MapChecks();

    public MapInspectionService(TomlSecretsConfig secrets, MapDownloader downloader, DetachedMapReadLock readLock) {
        this.secrets = secrets;
        this.downloader = downloader;
        this.readLock = readLock;
    }

    public Context capture() {
        GameThread.require("map inspection capture");
        return new Context(ContentRegistrySnapshot.capture(), InspectionSettings.capture(secrets.maps.checks));
    }

    /** The caller must dispatch this method to Async/StorageExecutor. No engine references cross this boundary. */
    public MapInspectionResult inspect(MapFileSourceV1 file, String requestedProfile, Context context) {
        MapDownloader.Download downloaded;
        try { downloaded = downloader.download(file.url(), context.settings().maxFileBytes()); }
        catch (IOException ex) { return MapInspectionResult.failed(MapInspectionResult.Status.DOWNLOAD_FAILED, ex.getMessage()); }
        try (downloaded) {
            MapSnapshot snapshot;
            readLock.lock();
            try { snapshot = reader.read(downloaded.path(), downloaded.sha256(), context.registry(), context.settings()); }
            finally { readLock.unlock(); }
            String profile = MapChecks.profile(snapshot, requestedProfile);
            return new MapInspectionResult(MapInspectionResult.Status.OK,
                    snapshot.tilesAvailable() ? null : "Tile limit exceeded; tile checks were skipped",
                    snapshot, profile, checks.check(snapshot, profile, context.settings()));
        } catch (IOException ex) { return MapInspectionResult.failed(MapInspectionResult.Status.INVALID_MAP, ex.getMessage()); }
    }

    public record Context(ContentRegistrySnapshot registry, InspectionSettings settings) {}
}
