package org.xcore.plugin.gamemode.laststanding;

import arc.Events;
import arc.struct.ObjectMap;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.content.Blocks;
import mindustry.entities.units.AIController;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.world.Block;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.gamemode.laststanding.LastStandingAi;

import static arc.Core.app;
import static mindustry.Vars.*;

@Singleton
public class LastStanding {
    public static final ObjectMap<Team, Block> spawnFloors = ObjectMap.of(
            Team.sharded, Blocks.metalFloor,
            Team.malis, Blocks.metalFloor2,
            Team.green, Blocks.metalFloor3,
            Team.blue, Blocks.metalFloor4
    );

    private final Config config;

    @Inject
    public LastStanding(Config config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        if (!config.isLastStanding()) return;

        Events.on(EventType.CoreChangeEvent.class,
                event -> app.post(() -> spawnFloors.each((team, floor) -> {
                    if (team.active()) return;

                    spawner.getSpawns().each(tile -> tile.floor() == floor, tile -> {
                        tile.setOverlayNet(Blocks.air);
                        spawner.getSpawns().remove(tile);
                    });
                })));

        Events.on(EventType.PlayEvent.class, event -> state.rules.waves = true);

        content.units().each(type -> {
            var controller = type.controller;
            type.controller = unit -> unit.team == state.rules.waveTeam && unit.type.aiController.get() instanceof AIController ai
                    ? new LastStandingAi(ai) : controller.get(unit);
        });
    }
}