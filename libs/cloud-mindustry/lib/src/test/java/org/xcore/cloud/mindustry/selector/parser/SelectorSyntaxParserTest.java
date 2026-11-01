package org.xcore.cloud.mindustry.selector.parser;

import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.type.UnitType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.SortOrder;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.exception.SelectorSyntaxException;

import static org.junit.jupiter.api.Assertions.*;

class SelectorSyntaxParserTest {

    @BeforeAll
    static void initMindustryContent() {
        Vars.state = new GameState();
        if (Vars.content == null) {
            Vars.content = new ContentLoader();
        }
        if (Vars.content.unit("dagger") == null) {
            new UnitType("dagger");
        }
        if (Vars.content.unit("flare") == null) {
            new UnitType("flare");
        }
    }

    @Test
    @DisplayName("Parse bare root selectors")
    void parse_bareSelectors() {
        TargetSelectorSpec a = SelectorSyntaxParser.parse("@a");
        assertEquals(SelectorKind.ALL_PLAYERS, a.kind());
        assertTrue(a.kind().defaultMultiple());

        TargetSelectorSpec p = SelectorSyntaxParser.parse("@p");
        assertEquals(SelectorKind.NEAREST_PLAYER, p.kind());
        assertEquals(SortOrder.NEAREST, p.sort());
        assertEquals(1, p.limit());

        TargetSelectorSpec s = SelectorSyntaxParser.parse("@s");
        assertEquals(SelectorKind.SELF, s.kind());
        assertEquals(1, s.limit());

        TargetSelectorSpec r = SelectorSyntaxParser.parse("@r");
        assertEquals(SelectorKind.RANDOM_PLAYER, r.kind());
        assertEquals(SortOrder.RANDOM, r.sort());
        assertEquals(1, r.limit());

        TargetSelectorSpec e = SelectorSyntaxParser.parse("@e");
        assertEquals(SelectorKind.ALL_ENTITIES, e.kind());
        assertFalse(e.isPlayerOnly());
    }

    @Test
    @DisplayName("Parse literal player name and #id")
    void parse_literalPlayers() {
        TargetSelectorSpec literalId = SelectorSyntaxParser.parse("#42");
        assertEquals(SelectorKind.LITERAL_PLAYER, literalId.kind());
        assertEquals("#42", literalId.literalName());

        TargetSelectorSpec literalName = SelectorSyntaxParser.parse("Anuke");
        assertEquals(SelectorKind.LITERAL_PLAYER, literalName.kind());
        assertEquals("Anuke", literalName.literalName());
    }

    @Test
    @DisplayName("Parse selector with team and distance criteria")
    void parse_bracketedCriteria() {
        TargetSelectorSpec spec = SelectorSyntaxParser.parse("@a[team=crux,distance=..50,limit=5,sort=furthest]");
        assertEquals(SelectorKind.ALL_PLAYERS, spec.kind());
        assertEquals(Team.crux, spec.team());
        assertFalse(spec.invertTeam());
        assertEquals(0f, spec.distanceMin());
        assertEquals(50f * Vars.tilesize, spec.distanceMax());
        assertEquals(5, spec.limit());
        assertEquals(SortOrder.FURTHEST, spec.sort());
    }

    @Test
    @DisplayName("Parse negated team and unit type")
    void parse_negations() {
        TargetSelectorSpec spec = SelectorSyntaxParser.parse("@e[team=!sharded,type=!dagger]");
        assertEquals(SelectorKind.ALL_ENTITIES, spec.kind());
        assertEquals(Team.sharded, spec.team());
        assertTrue(spec.invertTeam());
        assertNotNull(spec.unitType());
        assertEquals("dagger", spec.unitType().name);
        assertTrue(spec.invertType());
    }

    @Test
    @DisplayName("Parse relative coordinates")
    void parse_relativeCoords() {
        TargetSelectorSpec spec = SelectorSyntaxParser.parse("@p[x=~10,y=~-5]");
        assertTrue(spec.hasExplicitOrigin());
        assertTrue(spec.relativeX());
        assertTrue(spec.relativeY());
        assertEquals(10f * Vars.tilesize, spec.originX());
        assertEquals(-5f * Vars.tilesize, spec.originY());
    }

    @Test
    @DisplayName("Malformed distance range throws syntax exception")
    void parse_invalidRange_throwsException() {
        assertThrows(SelectorSyntaxException.class, () -> SelectorSyntaxParser.parse("@p[distance=50..10]"));
    }

    @Test
    @DisplayName("Unknown selector key throws syntax exception")
    void parse_unknownKey_throwsException() {
        assertThrows(SelectorSyntaxException.class, () -> SelectorSyntaxParser.parse("@a[invalid_key=value]"));
    }
}
