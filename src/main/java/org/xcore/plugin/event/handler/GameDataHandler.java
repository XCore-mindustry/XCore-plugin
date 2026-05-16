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
        // Tracking block destruction by player is not natively supported by BlockDestroyEvent.
    }
 
    public void onUnitCreate(UnitCreateEvent event) {
        if (event.unit != null) {
            Team team = event.unit.team();
            if (team != null && team != Team.derelict) {
                Groups.player.each(p -> {
                    if (p.team() == team) {
                        var stats = gameDataService.getStats(p.uuid());
                        if (stats != null) {
                            stats.setUnitsProduced(stats.getUnitsProduced() + 1);
                        }
                    }
                });
            }
        }
    }
 
    public void onUnitDestroy(UnitDestroyEvent event) {
        if (event.unit != null && event.unit.killer != null) {
            if (event.unit.killer.isPlayer()) {
                var stats = gameDataService.getStats(event.unit.killer.getPlayer().uuid());
                if (stats != null) {
                    stats.setUnitsDestroyed(stats.getUnitsDestroyed() + 1);
                }
            }
        }
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