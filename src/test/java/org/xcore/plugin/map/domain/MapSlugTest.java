package org.xcore.plugin.map.domain;

import arc.graphics.Color;
import arc.graphics.Colors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapSlugTest {
    @Test
    void stripsMarkupAndGlyphsWithoutDeletingMeaningfulBracketedText() {
        // Mindustry registers accent at startup; headless unit tests do not run startup.
        Color previous = Colors.get("accent");
        Colors.put("accent", Color.valueOf("ffd37f"));
        try {
            assertEquals("anuke/desert-pvp", MapSlug.of("[red]Anuke[]", "[accent]Desert[] [PvP] \uE800").asSlug());
        } finally {
            if (previous == null) Colors.getColors().remove("accent");
            else Colors.put("accent", previous);
        }
    }

    @Test
    void normalizesUnicodeWithoutErasingNonLatinNames() {
        assertEquals("автор/карта", MapSlug.of("АВТОР", "Карта").asSlug());
        assertEquals("作者/地图", MapSlug.of("作者", "地图").asSlug());
        assertEquals(MapSlug.of("René", "Café"), MapSlug.of("Rene\u0301", "Cafe\u0301"));
    }

    @Test
    void preservesSemanticCombiningMarksAndCanonicalComposition() {
        assertEquals("हिन्दी/ภาษาไทย", MapSlug.of("हिन्दी", "ภาษาไทย").asSlug());
        assertEquals("한글/지도", MapSlug.of("한글", "지도").asSlug());
        var slug = MapSlug.of("हिन्दी", "Cafe\u0301");
        assertEquals("हिन्दी/café", slug.asSlug());
        assertEquals(slug, MapSlug.of(slug.author(), slug.name()));
    }

    @Test
    void fallsBackAfterSanitizationForEmptyOrOnlyPunctuationValues() {
        assertEquals("community/unnamed", MapSlug.of(null, null).asSlug());
        assertEquals("community/unnamed", MapSlug.of("Unknown", "!!!").asSlug());
        assertEquals("community/unnamed", MapSlug.of("\uDB80\uDC00", "\uE800").asSlug());
    }

    @Test
    void cannotConstructNonCanonicalSegments() {
        assertThrows(IllegalArgumentException.class, () -> new MapSlug("author/name", "map"));
        assertThrows(IllegalArgumentException.class, () -> new MapSlug("", "map"));
        assertThrows(IllegalArgumentException.class, () -> new MapSlug("Author", "map"));
    }
}
