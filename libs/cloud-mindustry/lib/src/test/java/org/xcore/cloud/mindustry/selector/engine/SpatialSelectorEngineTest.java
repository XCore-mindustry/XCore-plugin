package org.xcore.cloud.mindustry.selector.engine;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.SortOrder;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.exception.NoSuchTargetException;
import org.xcore.cloud.mindustry.selector.exception.SelectorSenderRequirementException;
import org.xcore.cloud.mindustry.selector.parser.SelectorSyntaxParser;

import static org.junit.jupiter.api.Assertions.*;

class SpatialSelectorEngineTest {

    private SpatialSelectorEngine engine;

    @BeforeAll
    static void initMindustry() {
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

    @BeforeEach
    void setUp() {
        engine = new SpatialSelectorEngine();
        Groups.init();
    }

    private Player createMockPlayer(int id, String name, Team team, float x, float y, boolean admin) {
        Player p = Player.create();
        p.id = id;
        p.name = name;
        p.team(team);
        p.set(x, y);
        p.admin = admin;
        p.add();
        return p;
    }

    private Unit createMockUnit(int id, UnitType type, Team team, float x, float y, float health) {
        Unit u = type.create(team);
        u.id = id;
        u.set(x, y);
        u.health = health;
        u.maxHealth = 100f;
        u.add();
        return u;
    }

    @Test
    @DisplayName("@s resolves sender player, but throws for console")
    void resolve_self() {
        Player p1 = createMockPlayer(1, "PlayerOne", Team.sharded, 100, 100, false);
        MindustrySender playerSender = new MindustrySender.PlayerSender(p1);
        MindustrySender consoleSender = new MindustrySender.ConsoleSender();

        TargetSelectorSpec spec = SelectorSyntaxParser.parse("@s");

        Seq<Player> resolved = engine.resolvePlayers(playerSender, spec);
        assertEquals(1, resolved.size);
        assertEquals(p1, resolved.first());

        assertThrows(SelectorSenderRequirementException.class, () -> engine.resolvePlayers(consoleSender, spec));
    }

    @Test
    @DisplayName("@p resolves nearest player relative to origin")
    void resolve_nearestPlayer() {
        Player p1 = createMockPlayer(1, "NearPlayer", Team.sharded, 50, 50, false);
        Player p2 = createMockPlayer(2, "FarPlayer", Team.sharded, 500, 500, false);

        MindustrySender console = new MindustrySender.ConsoleSender();

        TargetSelectorSpec spec = SelectorSyntaxParser.parse("@p[x=0,y=0]");
        Seq<Player> resolved = engine.resolvePlayers(console, spec);

        assertEquals(1, resolved.size);
        assertEquals(p1, resolved.first());
    }

    @Test
    @DisplayName("@a[team=crux] filters players by team")
    void resolve_teamFilter() {
        Player p1 = createMockPlayer(1, "ShardedPlayer", Team.sharded, 10, 10, false);
        Player p2 = createMockPlayer(2, "CruxPlayer", Team.crux, 20, 20, false);

        MindustrySender console = new MindustrySender.ConsoleSender();
        TargetSelectorSpec spec = SelectorSyntaxParser.parse("@a[team=crux]");

        Seq<Player> resolved = engine.resolvePlayers(console, spec);
        assertEquals(1, resolved.size);
        assertEquals(p2, resolved.first());
    }

    @Test
    @DisplayName("@a[admin=true] filters admins only")
    void resolve_adminFilter() {
        createMockPlayer(1, "NormalUser", Team.sharded, 10, 10, false);
        Player admin = createMockPlayer(2, "AdminUser", Team.sharded, 20, 20, true);

        MindustrySender console = new MindustrySender.ConsoleSender();
        TargetSelectorSpec spec = SelectorSyntaxParser.parse("@a[admin=true]");

        Seq<Player> resolved = engine.resolvePlayers(console, spec);
        assertEquals(1, resolved.size);
        assertEquals(admin, resolved.first());
    }

    @Test
    @DisplayName("@e[type=dagger] filters units by type")
    void resolve_unitType() {
        UnitType dagger = Vars.content.unit("dagger");
        UnitType flare = Vars.content.unit("flare");

        Unit u1 = createMockUnit(10, dagger, Team.crux, 100, 100, 100);
        Unit u2 = createMockUnit(11, flare, Team.crux, 150, 150, 100);

        MindustrySender console = new MindustrySender.ConsoleSender();
        TargetSelectorSpec spec = SelectorSyntaxParser.parse("@e[type=dagger]");

        Seq<Unit> resolved = engine.resolveUnits(console, spec);
        assertEquals(1, resolved.size);
        assertEquals(u1, resolved.first());
    }

    @Test
    @DisplayName("Literal player lookup by clean name and #id")
    void resolve_literal() {
        Player p = createMockPlayer(42, "[scarlet]Colored[white]Name", Team.sharded, 10, 10, false);
        MindustrySender console = new MindustrySender.ConsoleSender();

        TargetSelectorSpec specById = SelectorSyntaxParser.parse("#42");
        Seq<Player> byId = engine.resolvePlayers(console, specById);
        assertEquals(1, byId.size);
        assertEquals(p, byId.first());

        TargetSelectorSpec specByName = SelectorSyntaxParser.parse("ColoredName");
        Seq<Player> byName = engine.resolvePlayers(console, specByName);
        assertEquals(1, byName.size);
        assertEquals(p, byName.first());

        TargetSelectorSpec notFound = SelectorSyntaxParser.parse("NonExistentPlayer");
        assertThrows(NoSuchTargetException.class, () -> engine.resolvePlayers(console, notFound));
    }
}
