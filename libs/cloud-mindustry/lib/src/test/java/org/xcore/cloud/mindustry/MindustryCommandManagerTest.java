package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import arc.util.Log;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.type.UnitType;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.cloud.mindustry.selector.annotation.AllowedSelectors;
import org.xcore.cloud.mindustry.selector.annotation.DenySelectors;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MindustryCommandManagerTest {

    private MindustryCommandManager<MindustrySender> manager;
    private AnnotationParser<MindustrySender> annotationParser;
    private List<String> messages;
    private List<String> executed;
    private Log.LogHandler previousLogger;
    private List<String> logged;

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
        manager = MindustryCommandManager.create(new CommandHandler(""));
        annotationParser = new AnnotationParser<>(manager, MindustrySender.class);
        manager.registerSelectorAnnotations(annotationParser);
        messages = new ArrayList<>();
        executed = new ArrayList<>();
        logged = new ArrayList<>();
        previousLogger = Log.logger;
        Log.logger = (level, text) -> logged.add(text);
    }

    @AfterEach
    void tearDown() {
        Log.logger = previousLogger;
    }

    private MindustrySender sender(Player player) {
        return new MindustrySender() {
            @Override public void sendMessage(String message) { messages.add(message); }
            @Override public String name() { return player != null ? player.plainName() : "Console"; }
            @Override public boolean isPlayer() { return player != null; }
            @Override public Player player() { return player; }
        };
    }

    private Player player(int id, String name, float x, float y) {
        Player p = Player.create();
        p.id = id;
        p.name = name;
        p.team(Team.sharded);
        p.set(x, y);
        p.add();
        return p;
    }

    private void run(MindustrySender sender, String input) {
        try {
            manager.commandExecutor().executeCommand(sender, input).join();
        } catch (Exception ignored) {
            // Failures are reported to the sender; the future still completes exceptionally.
        }
    }

    static final class CustomException extends RuntimeException {
        CustomException(String message) {
            super(message);
        }
    }

    @Test
    @DisplayName("Unexpected handler failures are logged, never shown verbatim")
    void unexpectedFailure_isLoggedNotLeaked() {
        annotationParser.parse(new Object() {
            @Command("boom")
            public void boom(MindustrySender sender) {
                throw new IllegalStateException("secret internal detail");
            }
        });

        run(sender(null), "boom");

        assertEquals(1, messages.size(), messages.toString());
        assertFalse(messages.get(0).contains("secret internal detail"), messages.toString());
        assertFalse(messages.get(0).isBlank());
        assertTrue(logged.stream().anyMatch(l -> l.contains("secret internal detail")), logged.toString());
    }

    @Test
    @DisplayName("Exceptions from postprocessors reach the handler registered for their type")
    void postprocessorException_isUnwrapped() {
        manager.exceptionController().registerHandler(CustomException.class,
                ctx -> ctx.context().sender().sendMessage("handled:" + ctx.exception().getMessage()));
        manager.registerCommandPostProcessor(ctx -> {
            throw new CustomException("guard");
        });
        annotationParser.parse(new Object() {
            @Command("guarded")
            public void guarded(MindustrySender sender) {
                executed.add("guarded");
            }
        });

        run(sender(null), "guarded");

        assertTrue(executed.isEmpty());
        assertEquals(List.of("handled:guard"), messages);
    }

    @Test
    @DisplayName("Exceptions from command handlers reach the handler registered for their type")
    void handlerException_isUnwrapped() {
        manager.exceptionController().registerHandler(CustomException.class,
                ctx -> ctx.context().sender().sendMessage("handled:" + ctx.exception().getMessage()));
        annotationParser.parse(new Object() {
            @Command("throws")
            public void handler(MindustrySender sender) {
                throw new CustomException("inside");
            }
        });

        run(sender(null), "throws");

        assertEquals(List.of("handled:inside"), messages);
    }

    @Test
    @DisplayName("Cloud's standard captions are not shadowed by the library's providers")
    void standardCaptions_areNotEmpty() {
        annotationParser.parse(new Object() {
            @Command("sum <a> <b>")
            public void sum(MindustrySender sender, @Argument("a") int a, @Argument("b") int b) {
            }
        });

        run(sender(null), "sum 1");
        run(sender(null), "sum 1 x");

        assertEquals(2, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("sum <a> <b>"), messages.toString());
        assertTrue(messages.get(1).contains("'x'"), messages.toString());
    }

    @Test
    @DisplayName("Selector parse failures are reported through their caption")
    void selectorFailure_usesCaption() {
        player(1, "Alice", 10, 10);
        annotationParser.parse(new Object() {
            @Command("direct <target>")
            public void direct(MindustrySender sender, @Argument("target") Player target) {
                executed.add("direct:" + target.plainName());
            }
        });

        run(sender(null), "direct Nobody");
        run(sender(null), "direct @q");

        assertTrue(executed.isEmpty());
        assertEquals(2, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("No targets matched selector"), messages.get(0));
        assertTrue(messages.get(1).contains("Invalid selector syntax"), messages.get(1));
    }

    @Test
    @DisplayName("Selector captions can be replaced, e.g. for localization")
    void selectorCaption_canBeOverridden() {
        manager.captionRegistry().registerProvider((caption, recipient) ->
                caption.key().equals("argument.parse.failure.selector.no_such_target") ? "nobody: <input>" : null);
        annotationParser.parse(new Object() {
            @Command("direct <target>")
            public void direct(MindustrySender sender, @Argument("target") Player target) {
            }
        });

        run(sender(null), "direct Ghost");

        assertEquals(List.of("[scarlet]nobody: Ghost"), messages);
    }

    @Test
    @DisplayName("Parameter-level @DenySelectors only restricts that argument")
    void parameterLevelDeny_restrictsOnlyThatArgument() {
        Player alice = player(1, "Alice", 10, 10);
        player(2, "Bob", 20, 20);
        annotationParser.parse(new Object() {
            @Command("give <from> <to>")
            public void give(MindustrySender sender,
                             @Argument("from") @DenySelectors(reason = "name the giver") Player from,
                             @Argument("to") SinglePlayerSelector to) {
                executed.add(from.plainName() + "->" + to.resolve(sender).plainName());
            }
        });

        MindustrySender sender = sender(alice);
        run(sender, "give @s Bob");
        assertTrue(executed.isEmpty());
        assertTrue(messages.stream().anyMatch(m -> m.contains("name the giver")), messages.toString());

        run(sender, "give Alice @s");
        assertEquals(List.of("Alice->Alice"), executed);
    }

    @Test
    @DisplayName("Parameter-level @AllowedSelectors only accepts the listed kinds")
    void parameterLevelAllowed_restrictsKinds() {
        Player alice = player(1, "Alice", 10, 10);
        annotationParser.parse(new Object() {
            @Command("look <target>")
            public void look(MindustrySender sender,
                             @Argument("target") @AllowedSelectors(SelectorKind.SELF) SinglePlayerSelector target) {
                executed.add("look:" + target.resolve(sender).plainName());
            }
        });

        MindustrySender sender = sender(alice);
        run(sender, "look @p");
        assertTrue(executed.isEmpty());

        run(sender, "look @s");
        assertEquals(List.of("look:Alice"), executed);
    }

    @Test
    @DisplayName("Command-level selector restrictions apply to every selector argument")
    void commandLevelRestrictions_checkEverySelector() {
        Player alice = player(1, "Alice", 10, 10);
        annotationParser.parse(new Object() {
            @Command("pair <a> <b>")
            @AllowedSelectors(SelectorKind.SELF)
            public void pair(MindustrySender sender,
                             @Argument("a") SinglePlayerSelector a,
                             @Argument("b") SinglePlayerSelector b) {
                executed.add("pair");
            }
        });

        MindustrySender sender = sender(alice);
        // The disallowed selector comes first: only checking the last one would let it through.
        run(sender, "pair @r @s");
        assertTrue(executed.isEmpty());

        run(sender, "pair @s @s");
        assertEquals(List.of("pair"), executed);
    }

    @Test
    @DisplayName("Team and content parsers are registered by default")
    void defaultMindustryParsers() {
        annotationParser.parse(new Object() {
            @Command("team <team>")
            public void team(MindustrySender sender, @Argument("team") Team team) {
                executed.add("team:" + team.name);
            }

            @Command("unit <type>")
            public void unit(MindustrySender sender, @Argument("type") UnitType type) {
                executed.add("unit:" + type.name);
            }
        });

        MindustrySender console = sender(null);
        run(console, "team CRUX");
        run(console, "team 1");
        run(console, "team 77");
        run(console, "unit Dagger");
        run(console, "unit nope");

        assertEquals(List.of("team:crux", "team:sharded", "unit:dagger"), executed);
        assertEquals(2, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("is not a valid team"), messages.get(0));
        assertTrue(messages.get(1).contains("is not a valid unit"), messages.get(1));
    }

    @Test
    @DisplayName("Console output has Mindustry color tags stripped")
    void consoleSender_stripsColors() {
        new MindustrySender.ConsoleSender().sendMessage("[scarlet]Error:[] something");

        assertEquals(1, logged.size());
        assertFalse(logged.get(0).contains("[scarlet]"), logged.get(0));
        assertTrue(logged.get(0).contains("Error: something"), logged.get(0));
    }
}
