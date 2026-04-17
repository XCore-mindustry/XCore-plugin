package org.xcore.plugin.startup;

import arc.util.Log;
import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.database.repository.MapDataRepository;

@Singleton
public class MapDecayScheduler {

    private static final float DECAY_INTERVAL_SECONDS = 60f * 60f;

    private final MapDataRepository mapDataRepository;

    @Inject
    public MapDecayScheduler(MapDataRepository mapDataRepository) {
        this.mapDataRepository = mapDataRepository;
    }

    public void initialize() {
        checkMapDecay("Failed to check map decay on init");
        Timer.schedule(() -> checkMapDecay("Failed to check map decay"), DECAY_INTERVAL_SECONDS, DECAY_INTERVAL_SECONDS);
    }

    private void checkMapDecay(String failureMessage) {
        try {
            mapDataRepository.checkMapDecay();
        } catch (Exception e) {
            Log.err(failureMessage, e);
        }
    }
}
