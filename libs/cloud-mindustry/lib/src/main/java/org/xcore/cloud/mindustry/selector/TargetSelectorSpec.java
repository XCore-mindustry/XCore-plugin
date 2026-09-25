package org.xcore.cloud.mindustry.selector;

import arc.util.Nullable;
import mindustry.game.Team;
import mindustry.type.UnitType;

/**
 * Immutable specification holding all parsed filter criteria for a target selector.
 */
public record TargetSelectorSpec(
        SelectorKind kind,
        @Nullable String literalName,
        @Nullable Team team,
        boolean invertTeam,
        @Nullable UnitType unitType,
        boolean invertType,
        float distanceMin,
        float distanceMax,
        float originX,
        float originY,
        boolean relativeX,
        boolean relativeY,
        @Nullable Boolean admin,
        @Nullable String name,
        boolean invertName,
        int limit,
        SortOrder sort
) {
    public static final int DEFAULT_PLAYER_LIMIT = 32;
    public static final int DEFAULT_UNIT_LIMIT = 50;
    public static final int MAX_ENTITIES_HARD_CAP = 100;

    public boolean hasDistance() {
        return distanceMin > 0f || distanceMax < Float.MAX_VALUE;
    }

    public boolean hasExplicitOrigin() {
        return !Float.isNaN(originX) && !Float.isNaN(originY);
    }

    public boolean isSingleTarget() {
        return kind == SelectorKind.SELF
                || kind == SelectorKind.NEAREST_PLAYER
                || kind == SelectorKind.RANDOM_PLAYER
                || kind == SelectorKind.LITERAL_PLAYER
                || limit == 1;
    }

    public boolean isPlayerOnly() {
        return kind != SelectorKind.ALL_ENTITIES;
    }
}
