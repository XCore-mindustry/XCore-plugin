package org.xcore.plugin.event.handler;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.*;
import org.xcore.plugin.service.GameDataService;

@Singleton
public class GameDataHandler {

    private final GameDataService gameDataService;

    @Inject
    public GameDataHandler(GameDataService gameDataService) {
        this.gameDataService = gameDataService;
    }

    public void onBlockBuild(BlockBuildEndEvent event) {
        if (event.unit != null && event.unit.isPlayer()) {
            var stats = gameDataService.getStats(event.unit.getPlayer().uuid());
            if (stats != null) {
                if (event.breaking)
                    stats.setBlocksDeconstructed(stats.getBlocksDeconstructed() + 1);
                else stats.setBlocksBuilt(stats.getBlocksBuilt() + 1);
            }
        }
    }

    public void onBlockDestroy(BlockDestroyEvent event) {

    }

    public void onUnitCreate(UnitCreateEvent event) {

    }

    public void onUnitDestroy(UnitDestroyEvent event) {

    }

    public void onPlayerJoin(PlayerJoin event) {
        if (event.player != null) {
            gameDataService.addPlayer(event.player);
        }
    }

    public void onPlayerLeave(PlayerLeave event) {
        if (event.player != null) {
            gameDataService.recordLeave(event.player);
        }
    }
}