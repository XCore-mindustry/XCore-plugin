package org.xcore.plugin.player;

import mindustry.gen.Iconc;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BadgeTest {

    @Test
    @DisplayName("tagWithGlyphColor keeps both brackets in the badge colour")
    void glyphColorKeepsBracketsInBadgeColor() {
        assertThat(Badge.DEVELOPER.tagWithGlyphColor("[#ff8844]"))
                .isEqualTo("[#86dca2]<[white][#ff8844]" + Iconc.wrench + "[][]>[]");
        assertThat(Badge.ADMIN.tagWithGlyphColor("[#ff8844]"))
                .isEqualTo("[scarlet]<[#ff8844]" + Iconc.hammer + "[]>[]");
    }

    @Test
    @DisplayName("tagWithGlyphColor leaves the colour stack as it found it")
    void glyphColorTagsAreBalanced() {
        for (Badge badge : Badge.values()) {
            String tag = badge.tagWithGlyphColor("[#ff8844]");
            int opened = tag.split("\\[[^\\]]+]", -1).length - 1;
            int closed = tag.split("\\[]", -1).length - 1;
            assertThat(closed).as(badge.id()).isEqualTo(opened);
        }
    }
}
