package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.annotation.PlayerOnly;
import org.xcore.cloud.mindustry.parser.MindustryCaptionKeys;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerOnlyTest {

    private static final String REFUSAL = "[scarlet]This command can only be used by an in-game player.";

    /** A sender type of the application's own, as XCore has: nothing in it says "player" to Cloud. */
    record AppSender(MindustrySender raw) {}

    private MindustryCommandManager<AppSender> manager;
    private AnnotationParser<AppSender> annotationParser;
    private List<String> messages;
    private List<String> executed;

    @BeforeAll
    static void initMindustry() {
        Vars.state = new GameState();
        Vars.headless = true;
        Vars.net = new mindustry.net.Net(null);
        if (Vars.content == null) {
            Vars.content = new ContentLoader();
        }
    }

    @BeforeEach
    void setUp() {
        Groups.init();
        manager = new MindustryCommandManager<>(
                new CommandHandler(""),
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.create(AppSender::new, AppSender::raw)
        );
        annotationParser = new AnnotationParser<>(manager, AppSender.class);
        manager.registerMindustryAnnotations(annotationParser);
        messages = new ArrayList<>();
        executed = new ArrayList<>();
    }

    private AppSender sender(Player player) {
        return new AppSender(new MindustrySender() {
            @Override public void sendMessage(String message) { messages.add(message); }
            @Override public String name() { return player != null ? player.plainName() : "Console"; }
            @Override public boolean isPlayer() { return player != null; }
            @Override public Player player() { return player; }
        });
    }

    private Player player() {
        Player p = Player.create();
        p.id = 1;
        p.name = "alice";
        p.team(Team.sharded);
        p.add();
        return p;
    }

    private void run(AppSender sender, String input) {
        try {
            manager.commandExecutor().executeCommand(sender, input).join();
        } catch (Exception ignored) {
            // Failures are reported to the sender; the future still completes exceptionally.
        }
    }

    @Test
    @DisplayName("@PlayerOnly on a method refuses the console and lets players through")
    void methodAnnotation() {
        annotationParser.parse(new Object() {
            @PlayerOnly
            @Command("home")
            public void home(AppSender sender) {
                executed.add("home");
            }

            @Command("version")
            public void version(AppSender sender) {
                executed.add("version");
            }
        });

        run(sender(null), "home");
        assertEquals(List.of(), executed);
        assertEquals(List.of(REFUSAL), messages);

        run(sender(null), "version");
        run(sender(player()), "home");
        assertEquals(List.of("version", "home"), executed);
        assertEquals(1, messages.size(), messages.toString());
    }

    @PlayerOnly
    public final class InGameCommands {
        @Command("spawn")
        public void spawn(AppSender sender) {
            executed.add("spawn");
        }
    }

    @Test
    @DisplayName("@PlayerOnly on a class covers every command in it")
    void classAnnotation() {
        annotationParser.parse(new InGameCommands());

        run(sender(null), "spawn");
        assertEquals(List.of(), executed);
        assertEquals(List.of(REFUSAL), messages);

        run(sender(player()), "spawn");
        assertEquals(List.of("spawn"), executed);
    }

    @Test
    @DisplayName("The builder API uses the PLAYER_ONLY meta key")
    void builderMeta() {
        var command = manager.commandBuilder("home")
                .meta(MindustryCommandManager.PLAYER_ONLY, true)
                .handler(ctx -> executed.add("home"))
                .build();
        manager.command(command);

        assertTrue(manager.isPlayerOnly(command));

        run(sender(null), "home");
        assertEquals(List.of(), executed);
        assertEquals(List.of(REFUSAL), messages);
    }

    @Test
    @DisplayName("The refusal is a caption, so it can be translated")
    void captionIsOverridable() {
        manager.captionRegistry().registerProvider((caption, sender) ->
                caption.equals(MindustryCaptionKeys.SENDER_PLAYER_REQUIRED) ? "Только для игроков." : null);
        annotationParser.parse(new Object() {
            @PlayerOnly
            @Command("home")
            public void home(AppSender sender) {
                executed.add("home");
            }
        });

        run(sender(null), "home");

        assertEquals(List.of("[scarlet]Только для игроков."), messages);
    }

    @Test
    @DisplayName("Input that does not parse reports the parse error, not the player requirement")
    void parseErrorComesFirst() {
        annotationParser.parse(new Object() {
            @PlayerOnly
            @Command("sethp <amount>")
            public void setHp(AppSender sender, @Argument("amount") int amount) {
                executed.add("sethp");
            }
        });

        run(sender(null), "sethp lots");
        assertEquals(1, messages.size(), messages.toString());
        assertNotEquals(REFUSAL, messages.get(0));

        run(sender(null), "sethp 5");
        assertEquals(REFUSAL, messages.get(1));
        assertEquals(List.of(), executed);
    }
}
