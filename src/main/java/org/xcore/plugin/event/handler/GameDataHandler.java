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

    public void onPickup(PickupEvent event) {
        if (event.build != null && event.carrier != null && event.carrier.isPlayer()) {
            var stats = gameDataService.getStats(event.carrier.getPlayer().uuid());
            if (stats != null) {
                stats.setBlocksDestroyed(stats.getBlocksDestroyed() + 1);
            }
        }
    }
 
    public void onBlockBuildBegin(BlockBuildBeginEvent event) {
        if (event.unit != null && event.unit.isPlayer() && event.breaking) {
            var stats = gameDataService.getStats(event.unit.getPlayer().uuid());
            if (stats != null) {
                stats.setBlocksDestroyed(stats.getBlocksDestroyed() + 1);
            }
        }
    }
 
    public void onBlockDestroy(BlockDestroyEvent event) {
        // This event is still handled for generic destructions where no specific player is identified.
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