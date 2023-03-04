package org.xcore.plugin.utils;

import arc.util.Time;
import arc.util.Tmp;
import mindustry.ai.types.CommandAI;

public class AttackAi extends CommandAI {

    public static long inactivityInterval = 4_000;

    public long lastCommandTime = -1;

    @Override
    public void updateUnit() {
        if (!hasCommand() && Time.timeSinceMillis(lastCommandTime) > inactivityInterval) {
            attackTarget = unit.closestEnemyCore();
            targetPos = Tmp.v1.set(attackTarget);
        } else {
            super.updateUnit();
            if (hasCommand()) {
                lastCommandTime = Time.millis();
            }
        }
    }
}
