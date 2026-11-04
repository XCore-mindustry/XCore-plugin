package org.xcore.cloud.mindustry.selector.engine;

import arc.struct.Seq;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.core.World;
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
        // Adding units and giving players one goes through Call, which needs a Net, and the
        // unit cap would kill units beyond what a team without cores may have.
        Vars.headless = true;
        Vars.net = new mindustry.net.Net(null);
        Vars.state.rules.disableUnitCap = true;
        // since v160 adding a physics unit registers it with PhysicsProcess, which reads the world bounds
        if (Vars.world == null) Vars.world = new World();
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

    private Player playerWithUnit(int id, String name, Team team, UnitType type, int unitId, float x, float y) {
        Player p = createMockPlayer(id, name, team, x, y, false);
        Unit u = createMockUnit(unitId, type, team, x, y, 100);
        p.unit(u);
        return p;
    }

    @Test
    @DisplayName("@s for units resolves the sender's own unit, not whichever unit comes first")
    void resolveUnits_selfIsSendersUnit() {
        UnitType dagger = Vars.content.unit("dagger");
        createMockUnit(10, dagger, Team.crux, 900, 900, 100);
        Player me = playerWithUnit(1, "Me", Team.sharded, dagger, 11, 100, 100);

        Seq<Unit> resolved = engine.resolveUnits(new MindustrySender.PlayerSender(me), SelectorSyntaxParser.parse("@s"));

        assertEquals(1, resolved.size);
        assertEquals(me.unit(), resolved.first());
    }

    @Test
    @DisplayName("@s for units from the console needs a player")
    void resolveUnits_selfFromConsoleThrows() {
        createMockUnit(10, Vars.content.unit("dagger"), Team.crux, 900, 900, 100);

        assertThrows(SelectorSenderRequirementException.class,
                () -> engine.resolveUnits(new MindustrySender.ConsoleSender(), SelectorSyntaxParser.parse("@s")));
    }

    @Test
    @DisplayName("@a for units selects the units players control, skipping players without one")
    void resolveUnits_allPlayersSelectsPlayerUnits() {
        UnitType dagger = Vars.content.unit("dagger");
        createMockUnit(10, dagger, Team.crux, 50, 50, 100);
        Player alice = playerWithUnit(1, "Alice", Team.sharded, dagger, 11, 100, 100);
        Player bob = playerWithUnit(2, "Bob", Team.crux, dagger, 12, 200, 200);
        createMockPlayer(3, "Unitless", Team.sharded, 300, 300, false);

        Seq<Unit> resolved = engine.resolveUnits(new MindustrySender.ConsoleSender(), SelectorSyntaxParser.parse("@a"));

        assertEquals(2, resolved.size);
        assertTrue(resolved.contains(alice.unit(), true));
        assertTrue(resolved.contains(bob.unit(), true));
    }

    @Test
    @DisplayName("player selectors for units honour player filters and the unit type")
    void resolveUnits_playerSelectorFilters() {
        UnitType dagger = Vars.content.unit("dagger");
        UnitType flare = Vars.content.unit("flare");
        playerWithUnit(1, "Alice", Team.sharded, dagger, 11, 100, 100);
        Player bob = playerWithUnit(2, "Bob", Team.crux, flare, 12, 200, 200);
        Player carol = playerWithUnit(3, "Carol", Team.crux, dagger, 13, 300, 300);
        MindustrySender console = new MindustrySender.ConsoleSender();

        Seq<Unit> crux = engine.resolveUnits(console, SelectorSyntaxParser.parse("@a[team=crux]"));
        assertEquals(2, crux.size);

        Seq<Unit> flares = engine.resolveUnits(console, SelectorSyntaxParser.parse("@a[type=flare]"));
        assertEquals(1, flares.size);
        assertEquals(bob.unit(), flares.first());

        Seq<Unit> nearestDagger = engine.resolveUnits(console,
                SelectorSyntaxParser.parse("@p[x=40,y=40,type=dagger,team=crux]"));
        assertEquals(1, nearestDagger.size);
        assertEquals(carol.unit(), nearestDagger.first());
    }

    @Test
    @DisplayName("a player name for units selects that player's unit")
    void resolveUnits_literalSelectsPlayersUnit() {
        UnitType dagger = Vars.content.unit("dagger");
        createMockUnit(10, dagger, Team.crux, 50, 50, 100);
        Player bob = playerWithUnit(2, "Bob", Team.crux, dagger, 12, 200, 200);
        MindustrySender console = new MindustrySender.ConsoleSender();

        Seq<Unit> resolved = engine.resolveUnits(console, SelectorSyntaxParser.parse("Bob"));
        assertEquals(1, resolved.size);
        assertEquals(bob.unit(), resolved.first());

        assertThrows(NoSuchTargetException.class,
                () -> engine.resolveUnits(console, SelectorSyntaxParser.parse("Nobody")));
    }

    @Test
    @DisplayName("@e[sort=nearest] sorts every candidate before the limit applies")
    void resolveUnits_sortBeforeLimit() {
        UnitType dagger = Vars.content.unit("dagger");
        for (int i = 0; i < 5; i++) {
            createMockUnit(100 + i, dagger, Team.crux, 5000, 5000 + i * 10, 100);
        }
        Unit nearest = createMockUnit(200, dagger, Team.crux, 12, 12, 100);

        Seq<Unit> resolved = engine.resolveUnits(new MindustrySender.ConsoleSender(),
                SelectorSyntaxParser.parse("@e[x=0,y=0,sort=nearest,limit=1]"));

        assertEquals(1, resolved.size);
        assertEquals(nearest, resolved.first());
    }

    @Test
    @DisplayName("@e[type=...,sort=health] sorts every unit of that type before the limit applies")
    void resolveUnits_sortBeforeLimitByType() {
        UnitType dagger = Vars.content.unit("dagger");
        for (int i = 0; i < 5; i++) {
            createMockUnit(100 + i, dagger, Team.crux, 5000, 5000 + i * 10, 100);
        }
        Unit weakest = createMockUnit(200, dagger, Team.crux, 3000, 3000, 10);

        Seq<Unit> resolved = engine.resolveUnits(new MindustrySender.ConsoleSender(),
                SelectorSyntaxParser.parse("@e[type=dagger,sort=health,limit=1]"));

        assertEquals(1, resolved.size);
        assertEquals(weakest, resolved.first());
    }

    @Test
    @DisplayName("@e finds units beyond the first few hundred")
    void resolveUnits_noScanCeiling() {
        UnitType dagger = Vars.content.unit("dagger");
        for (int i = 0; i < 600; i++) {
            createMockUnit(1000 + i, dagger, Team.sharded, 100, 100, 100);
        }
        Unit flare = createMockUnit(5000, Vars.content.unit("flare"), Team.sharded, 100, 100, 100);

        Seq<Unit> resolved = engine.resolveUnits(new MindustrySender.ConsoleSender(),
                SelectorSyntaxParser.parse("@e[type=flare]"));

        assertEquals(1, resolved.size);
        assertEquals(flare, resolved.first());
    }

    @Test
    @DisplayName("@s for units honours a team filter, like the other player selectors")
    void resolveUnits_selfHonoursTeam() {
        Player me = playerWithUnit(1, "Me", Team.sharded, Vars.content.unit("dagger"), 11, 100, 100);
        MindustrySender sender = new MindustrySender.PlayerSender(me);

        assertEquals(0, engine.resolveUnits(sender, SelectorSyntaxParser.parse("@s[team=crux]")).size);
        assertEquals(1, engine.resolveUnits(sender, SelectorSyntaxParser.parse("@s[team=sharded]")).size);
    }

    @Test
    @DisplayName("player selectors for units look past the first 100 players without a unit")
    void resolveUnits_playersBeyondTheCap() {
        for (int i = 0; i < 120; i++) {
            createMockPlayer(100 + i, "Idle" + i, Team.sharded, 10, 10, false);
        }
        Player active = playerWithUnit(1, "Active", Team.sharded, Vars.content.unit("dagger"), 11, 100, 100);

        Seq<Unit> resolved = engine.resolveUnits(new MindustrySender.ConsoleSender(), SelectorSyntaxParser.parse("@a"));

        assertEquals(1, resolved.size);
        assertEquals(active.unit(), resolved.first());
    }

    @Test
    @DisplayName("a literal name matches without colors, surrounding spaces or repeated spaces")
    void resolve_literalIgnoresSpacing() {
        Player p = createMockPlayer(5, "Big  Bob [white]", Team.sharded, 10, 10, false);

        Seq<Player> resolved = engine.resolvePlayers(new MindustrySender.ConsoleSender(), SelectorSyntaxParser.parse("big bob"));

        assertEquals(1, resolved.size);
        assertEquals(p, resolved.first());
    }

    @Test
    @DisplayName("@e with a distance sorts every unit in range before the limit applies")
    void resolveUnits_spatialSortBeforeLimit() {
        UnitType dagger = Vars.content.unit("dagger");
        createMockUnit(10, dagger, Team.crux, 60, 0, 100);
        Unit nearest = createMockUnit(11, dagger, Team.crux, 10, 0, 100);
        // The game loop fills the unit tree each frame (EntityCollisions.updatePhysics); do it here.
        Groups.resize(-200, -200, 400, 400);
        Groups.unit.tree().fill(Groups.unit.rawSeq());

        Seq<Unit> resolved = engine.resolveUnits(new MindustrySender.ConsoleSender(),
                SelectorSyntaxParser.parse("@e[x=0,y=0,distance=..20,sort=nearest,limit=1]"));

        assertEquals(1, resolved.size);
        assertEquals(nearest, resolved.first());
    }

    @Test
    @DisplayName("a literal name prefers the exact name over one that differs only in spacing")
    void resolve_literalPrefersExactName() {
        Player doubleSpaced = createMockPlayer(1, "Big  Bob", Team.sharded, 10, 10, false);
        Player single = createMockPlayer(2, "Big Bob", Team.sharded, 20, 20, false);
        MindustrySender console = new MindustrySender.ConsoleSender();

        assertEquals(single, engine.resolvePlayers(console, SelectorSyntaxParser.parse("Big Bob")).first());
        assertEquals(doubleSpaced, engine.resolvePlayers(console, SelectorSyntaxParser.parse("Big  Bob")).first());
    }
}
