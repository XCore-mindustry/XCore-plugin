package org.xcore.plugin.startup;

import arc.util.Log;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.database.repository.MapDataRepository;

@Singleton
public class MapDecayScheduler {

    private static final float DECAY_INTERVAL_SECONDS = 60f * 60f;

    private final MapDataRepository mapDataRepository;
    private final ScheduledTasks tasks = new ScheduledTasks(MapDecayScheduler.class);

    @Inject
    public MapDecayScheduler(MapDataRepository mapDataRepository) {
        this.mapDataRepository = mapDataRepository;
    }

    public void initialize() {
        checkMapDecay("Failed to check map decay on init");
        tasks.every(DECAY_INTERVAL_SECONDS, DECAY_INTERVAL_SECONDS, () -> checkMapDecay("Failed to check map decay"));
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        tasks.close();
    }

    private void checkMapDecay(String failureMessage) {
        try {
            mapDataRepository.checkMapDecay();
        } catch (Exception e) {
            Log.err(failureMessage, e);
        }
    }
}
