package org.xcore.plugin.startup;

import arc.util.Log;
import arc.util.Timer;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.MapDataRepository;

@Singleton
public class MapDecayScheduler {

    private static final float DECAY_INTERVAL_SECONDS = 60f * 60f;

    private final MapDataRepository mapDataRepository;
    private Timer.Task decayTask;
    private volatile boolean stopped;

    @Inject
    public MapDecayScheduler(MapDataRepository mapDataRepository) {
        this.mapDataRepository = mapDataRepository;
    }

    public void initialize() {
        checkMapDecay("Failed to check map decay on init");
        decayTask = Timer.schedule(() -> checkMapDecay("Failed to check map decay"), DECAY_INTERVAL_SECONDS, DECAY_INTERVAL_SECONDS);
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        stopped = true;
        if (decayTask != null) {
            decayTask.cancel();
            decayTask = null;
        }
    }

    private void checkMapDecay(String failureMessage) {
        if (stopped) return;
        try {
            mapDataRepository.checkMapDecay();
        } catch (Exception e) {
            Log.err(failureMessage, e);
        }
    }
}
