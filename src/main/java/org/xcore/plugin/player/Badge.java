package org.xcore.plugin.player;

import mindustry.gen.Iconc;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum Badge {
    ADMIN("admin", "[scarlet]<", Iconc.hammer, ">[]", "badge-admin-name", "badge-admin-description", BadgeType.SYSTEM, false),
    DEVELOPER("developer", "[#86dca2]<[white]", Iconc.wrench, "[]>[]", "badge-developer-name", "badge-developer-description", BadgeType.MANUAL, true),
    TRANSLATOR("translator", "[#79d7ff]<[white]", Iconc.bookOpen, "[]>[]", "badge-translator-name", "badge-translator-description", BadgeType.MANUAL, true),
    MAP_MAKER("map-maker", "[#caa56b]<[white]", Iconc.map, "[]>[]", "badge-map-maker-name", "badge-map-maker-description", BadgeType.MANUAL, true),
    CONTRIBUTOR("contributor", "[gold]<[white]", Iconc.star, "[]>[]", "badge-contributor-name", "badge-contributor-description", BadgeType.MANUAL, true),
    EVENT_WINNER("event-winner", "[#ffef88]<[white]", Iconc.itemSurgeAlloy, "[]>[]", "badge-event-winner-name", "badge-event-winner-description", BadgeType.MANUAL, true),
    VETERAN("veteran", "[cyan]<[white]", Iconc.ok, "[]>[]", "badge-veteran-name", "badge-veteran-description", BadgeType.MANUAL, true);

    private final String id;
    private final String tagPrefix;
    private final char glyph;
    private final String tagSuffix;
    private final String nameKey;
    private final String descriptionKey;
    private final BadgeType type;
    private final boolean selectable;

    Badge(String id, String tagPrefix, char glyph, String tagSuffix, String nameKey, String descriptionKey, BadgeType type, boolean selectable) {
        this.id = id;
        this.tagPrefix = tagPrefix;
        this.glyph = glyph;
        this.tagSuffix = tagSuffix;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.type = type;
        this.selectable = selectable;
    }

    public String id() {
        return id;
    }

    public String tag() {
        return tagPrefix + glyph + tagSuffix;
    }

    public String nameKey() {
        return nameKey;
    }

    public String descriptionKey() {
        return descriptionKey;
    }

    public BadgeType type() {
        return type;
    }

    public boolean selectable() {
        return selectable;
    }

    public boolean system() {
        return type == BadgeType.SYSTEM;
    }

    public static Badge byId(String id) {
        String normalizedId = normalizeId(id);
        if (normalizedId.isBlank()) return null;

        return Arrays.stream(values())
                .filter(badge -> normalizeId(badge.id).equals(normalizedId))
                .findFirst()
                .orElse(null);
    }

    public static List<Badge> selectableManualBadges() {
        return Arrays.stream(values())
                .filter(badge -> badge.type == BadgeType.MANUAL && badge.selectable)
                .toList();
    }

    public static String normalizeId(String id) {
        if (id == null) return "";
        return id.trim().toLowerCase(Locale.ROOT);
    }

    public enum BadgeType {
        SYSTEM,
        MANUAL,
        ACHIEVEMENT
    }
}
