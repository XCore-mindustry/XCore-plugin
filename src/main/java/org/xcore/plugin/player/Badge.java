package org.xcore.plugin.player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum Badge {
    ADMIN("admin", "[scarlet]<\uE817>[]", "badge-admin-name", "badge-admin-description", BadgeType.SYSTEM, false),
    DEVELOPER("developer", "[#86dca2]<[white]\uE80F[]>[]", "badge-developer-name", "badge-developer-description", BadgeType.MANUAL, true),
    TRANSLATOR("translator", "[#79d7ff]<[white]\uE80A[]>[]", "badge-translator-name", "badge-translator-description", BadgeType.MANUAL, true),
    MAP_MAKER("map-maker", "[#caa56b]<[white]\uE81B[]>[]", "badge-map-maker-name", "badge-map-maker-description", BadgeType.MANUAL, true),
    CONTRIBUTOR("contributor", "[gold]<[white]\uE809[]>[]", "badge-contributor-name", "badge-contributor-description", BadgeType.MANUAL, true),
    EVENT_WINNER("event-winner", "[#ffef88]<[white]\uF82C[]>[]", "badge-event-winner-name", "badge-event-winner-description", BadgeType.MANUAL, true),
    VETERAN("veteran", "[cyan]<[white]\uE800[]>[]", "badge-veteran-name", "badge-veteran-description", BadgeType.MANUAL, true);

    private final String id;
    private final String tag;
    private final String nameKey;
    private final String descriptionKey;
    private final BadgeType type;
    private final boolean selectable;

    Badge(String id, String tag, String nameKey, String descriptionKey, BadgeType type, boolean selectable) {
        this.id = id;
        this.tag = tag;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.type = type;
        this.selectable = selectable;
    }

    public String id() {
        return id;
    }

    public String tag() {
        return tag;
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
        if (id == null || id.isBlank()) return null;
        return Arrays.stream(values())
                .filter(badge -> badge.id.equalsIgnoreCase(id.trim()))
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
