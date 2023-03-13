package org.xcore.plugin.utils.ai;

import arc.math.geom.Geometry;
import mindustry.entities.Units;
import mindustry.entities.units.AIController;
import mindustry.game.Team;
import mindustry.gen.Teamc;
import mindustry.world.meta.BlockFlag;

import static mindustry.Vars.indexer;

public class LastStandingAi extends AIController {

    public Team targetTeam;

    public LastStandingAi(AIController fallback) {
        this.fallback = fallback;
    }

    @Override
    public void updateUnit() {
        super.updateUnit();

        if (targetTeam == null) {
            var core = unit.closestEnemyCore();
            if (core == null || core.team == Team.derelict) return;

            targetTeam = core.team;
        } else if (targetTeam.data().noCores()) {
            unit.kill();
        }
    }

    @Override
    public boolean useFallback() {
        return true;
    }

    @Override
    public AIController fallback() {
        return fallback;
    }

    @Override
    public Teamc target(float x, float y, float range, boolean air, boolean ground) {
        var targetUnit = Units.closest(targetTeam, x, y, range, unit -> unit.checkTarget(air, ground));
        if (targetUnit != null) return targetUnit;

        var targetBuilding = Units.findAllyTile(targetTeam, x, y, range, building -> ground);
        if (targetBuilding != null) return targetBuilding;

        return super.target(x, y, range, air, ground);
    }

    @Override
    public Teamc targetFlag(float x, float y, BlockFlag flag, boolean enemy) {
        return Geometry.findClosest(x, y, enemy ? indexer.getFlagged(targetTeam, flag) : indexer.getFlagged(unit.team, flag));
    }
}