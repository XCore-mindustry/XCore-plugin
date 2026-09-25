package org.xcore.cloud.mindustry.selector;

public enum SortOrder {
    NEAREST,
    FURTHEST,
    RANDOM,
    HEALTH_ASC,
    HEALTH_DESC,
    ARBITRARY;

    public static SortOrder fromString(String value) {
        return switch (value.toLowerCase()) {
            case "nearest", "near" -> NEAREST;
            case "furthest", "far" -> FURTHEST;
            case "random", "rand" -> RANDOM;
            case "health_asc", "health", "hp_asc" -> HEALTH_ASC;
            case "health_desc", "hp_desc" -> HEALTH_DESC;
            case "arbitrary", "none" -> ARBITRARY;
            default -> throw new IllegalArgumentException("Unknown sort order: " + value);
        };
    }
}
