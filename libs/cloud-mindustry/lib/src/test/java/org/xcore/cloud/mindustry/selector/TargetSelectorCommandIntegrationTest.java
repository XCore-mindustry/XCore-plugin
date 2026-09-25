package org.xcore.cloud.mindustry.selector;

import arc.struct.Seq;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.gen.Unit;
import mindustry.type.UnitType;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultiplePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.MultipleUnitSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelector.SingleUnitSelector;
import org.xcore.cloud.mindustry.selector.annotation.AllowedSelectors;
import org.xcore.cloud.mindustry.selector.annotation.DenySelectors;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TargetSelectorCommandIntegrationTest {

    private CommandHandler handler;
    private MindustryCommandManager<MindustrySender> manager;
    private List<String> lastMessages;

    @BeforeAll
    static void initMindustry() {
        Vars.state = new GameState();
        Vars.headless = true;
        Vars.net = new mindustry.net.Net(null);
        if (Vars.content == null) {
            Vars.content = new ContentLoader();
        }
        if (Vars.content.unit("dagger") == null) {
            new UnitType("dagger");
        }
    }

    @BeforeEach
    void setUp() {
        Groups.init();
        handler = new CommandHandler("");
        manager = MindustryCommandManager.create(handler);
        lastMessages = new ArrayList<>();
    }

    private MindustrySender createTestSender(Player player) {
        return new MindustrySender() {
            @Override
            public void sendMessage(String message) {
                lastMessages.add(message);
            }

            @Override
            public String name() {
                return player != null ? player.plainName() : "Console";
            }

            @Override
            public boolean isPlayer() {
                return player != null;
            }

            @Override
            public Player player() {
                return player;
            }
        };
    }

    public static class TestCommands {
        public final List<String> executed = new ArrayList<>();

        @Command("heal <target>")
        public void heal(MindustrySender sender, @Argument("target") SinglePlayerSelector target) {
            Player p = target.resolve(sender);
            executed.add("heal:" + p.plainName());
        }

        @Command("killall <targets>")
        public void killAll(MindustrySender sender, @Argument("targets") MultiplePlayerSelector targets) {
            Seq<Player> list = targets.resolve(sender);
            for (Player p : list) {
                executed.add("killall:" + p.plainName());
            }
        }

        @Command("direct <target>")
        public void direct(MindustrySender sender, @Argument("target") Player target) {
            executed.add("direct:" + target.plainName());
        }

        @Command("kick <target>")
        @DenySelectors(reason = "You cannot use selectors to kick players")
        public void kick(MindustrySender sender, @Argument("target") Player target) {
            executed.add("kick:" + target.plainName());
        }

        @Command("inspect <target>")
        @AllowedSelectors({SelectorKind.SELF, SelectorKind.NEAREST_PLAYER})
        public void inspect(MindustrySender sender, @Argument("target") SinglePlayerSelector target) {
            Player p = target.resolve(sender);
            executed.add("inspect:" + p.plainName());
        }

        @Command("killunits <targets>")
        public void killUnits(MindustrySender sender, @Argument("targets") MultipleUnitSelector targets) {
            Seq<Unit> list = targets.resolve(sender);
            executed.add("killunits:" + list.size);
        }
    }

    @Test
    @DisplayName("Single and multiple selectors execute smoothly through Cloud pipeline")
    void commandPipeline_executesSelectors() {
        Player p1 = Player.create();
        p1.id = 1;
        p1.name = "Alice";
        p1.team(Team.sharded);
        p1.set(100, 100);
        p1.add();

        Player p2 = Player.create();
        p2.id = 2;
        p2.name = "Bob";
        p2.team(Team.crux);
        p2.set(200, 200);
        p2.add();

        TestCommands cmd = new TestCommands();
        AnnotationParser<MindustrySender> annotationParser = new AnnotationParser<>(manager, MindustrySender.class);
        manager.registerSelectorAnnotations(annotationParser);
        annotationParser.parse(cmd);

        MindustrySender sender = createTestSender(p1);

        // 1. @s targeting self
        manager.commandExecutor().executeCommand(sender, "heal @s").join();
        assertTrue(cmd.executed.contains("heal:Alice"));

        // 2. @a[team=crux] targeting Bob
        manager.commandExecutor().executeCommand(sender, "killall @a[team=crux]").join();
        assertTrue(cmd.executed.contains("killall:Bob"));

        // 3. Direct Player parameter resolving @s
        manager.commandExecutor().executeCommand(sender, "direct @s").join();
        assertTrue(cmd.executed.contains("direct:Alice"));

        // 4. Direct Player parameter resolving by name
        manager.commandExecutor().executeCommand(sender, "direct Bob").join();
        assertTrue(cmd.executed.contains("direct:Bob"));
    }

    @Test
    @DisplayName("@DenySelectors blocks selector usage but allows literal names")
    void denySelectors_blocksSelectors() {
        Player p1 = Player.create();
        p1.id = 1;
        p1.name = "Alice";
        p1.set(10, 10);
        p1.add();

        TestCommands cmd = new TestCommands();
        AnnotationParser<MindustrySender> annotationParser = new AnnotationParser<>(manager, MindustrySender.class);
        manager.registerSelectorAnnotations(annotationParser);
        annotationParser.parse(cmd);

        MindustrySender sender = createTestSender(p1);

        // Trying selector on @DenySelectors command
        try {
            manager.commandExecutor().executeCommand(sender, "kick @p").join();
        } catch (Exception ignored) {}

        assertTrue(cmd.executed.isEmpty(), "Command should not execute on denied selector");
        assertTrue(lastMessages.stream().anyMatch(m -> m.contains("cannot use selectors to kick players")));

        // Literal name should succeed
        manager.commandExecutor().executeCommand(sender, "kick Alice").join();
        assertTrue(cmd.executed.contains("kick:Alice"));
    }

    @Test
    @DisplayName("@AllowedSelectors restricts allowed selector kinds")
    void allowedSelectors_enforcesAllowedKinds() {
        Player p1 = Player.create();
        p1.id = 1;
        p1.name = "Alice";
        p1.set(10, 10);
        p1.add();

        TestCommands cmd = new TestCommands();
        AnnotationParser<MindustrySender> annotationParser = new AnnotationParser<>(manager, MindustrySender.class);
        manager.registerSelectorAnnotations(annotationParser);
        annotationParser.parse(cmd);

        MindustrySender sender = createTestSender(p1);

        // @s is allowed
        manager.commandExecutor().executeCommand(sender, "inspect @s").join();
        assertTrue(cmd.executed.contains("inspect:Alice"));

        // @r is NOT in allowed list
        cmd.executed.clear();
        try {
            manager.commandExecutor().executeCommand(sender, "inspect @r").join();
        } catch (Exception ignored) {}
        assertTrue(cmd.executed.isEmpty());
        assertTrue(lastMessages.stream().anyMatch(m -> m.contains("not allowed for this command")));
    }

    @Test
    @DisplayName("Unit selector @e[type=dagger] resolves units via command")
    void unitSelector_executesViaCommand() {
        UnitType dagger = Vars.content.unit("dagger");
        dagger.useUnitCap = false;
        Unit u1 = dagger.create(Team.sharded);
        u1.id = 10;
        u1.set(100, 100);
        u1.health = 100;
        u1.maxHealth = 100;
        u1.spawnedByCore = true;
        u1.add();

        TestCommands cmd = new TestCommands();
        AnnotationParser<MindustrySender> annotationParser = new AnnotationParser<>(manager, MindustrySender.class);
        manager.registerSelectorAnnotations(annotationParser);
        annotationParser.parse(cmd);

        MindustrySender console = createTestSender(null);
        manager.commandExecutor().executeCommand(console, "killunits @e[type=dagger]").join();
        assertTrue(cmd.executed.contains("killunits:1"));
    }
}
