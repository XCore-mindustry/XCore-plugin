package org.xcore.plugin.modules;

import arc.Events;
import arc.struct.ObjectMap;
import mindustry.content.Blocks;
import mindustry.entities.units.AIController;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.world.Block;
import org.xcore.plugin.utils.LastStandingAi;

import static mindustry.Vars.*;
import static org.xcore.plugin.PluginVars.config;

public class LastStanding {
    public static final ObjectMap<Team, Block> spawnFloors = ObjectMap.of(
            Team.sharded, Blocks.metalFloor,
            Team.malis, Blocks.metalFloor2,
            Team.green, Blocks.metalFloor3,
            Team.blue, Blocks.metalFloor4
    );

    public static void init() {
        if (!config.isLastStanding()) return;

        Events.run(EventType.Trigger.update, () -> {
            spawnFloors.each((team, floor) -> {
                if (team.active()) return;

                spawner.getSpawns().each(tile -> tile.floor() == floor, tile -> {
                    tile.setOverlayNet(Blocks.air);
                    spawner.getSpawns().remove(tile);
                });
            });
        });

        content.units().each(type -> {
            var controller = type.controller;
            type.controller = unit -> unit.team == state.rules.waveTeam && unit.type.aiController.get() instanceof AIController ai ? new LastStandingAi(ai) : controller.get(unit);
        });
    }
}
