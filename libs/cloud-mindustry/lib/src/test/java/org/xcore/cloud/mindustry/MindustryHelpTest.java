package org.xcore.cloud.mindustry;

import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.incendo.cloud.parser.standard.StringParser;
import org.incendo.cloud.permission.Permission;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.parser.MindustryCaptionKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MindustryHelpTest {

    /** A sender type of the application's own, with its permissions attached. */
    record AppSender(MindustrySender raw, Set<String> permissions) {}

    private CommandHandler handler;
    private MindustryCommandManager<AppSender> manager;
    private List<String> messages;

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
        handler = new CommandHandler("/");
        handler.register("sync", "Re-synchronize world state.", args -> {});
        handler.register("t", "<message...>", "Send a message only to your teammates.", args -> {});
        manager = new MindustryCommandManager<>(
                handler,
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.create(raw -> new AppSender(raw, Set.of()), AppSender::raw)
        );
        manager.setPermissionChecker((sender, permission) -> sender.permissions().contains(permission));
        messages = new ArrayList<>();
    }

    private AppSender sender(Player player, String... permissions) {
        return new AppSender(new MindustrySender() {
            @Override public void sendMessage(String message) { messages.addAll(Arrays.asList(message.split("\n"))); }
            @Override public String name() { return player != null ? player.plainName() : "Console"; }
            @Override public boolean isPlayer() { return player != null; }
            @Override public Player player() { return player; }
        }, new HashSet<>(Arrays.asList(permissions)));
    }

    private Player player() {
        Player p = Player.create();
        p.id = 1;
        p.name = "alice";
        p.team(Team.sharded);
        p.add();
        return p;
    }

    private void command(String name, String description, String permission, String... aliases) {
        manager.command(manager.commandBuilder(name, aliases)
                .commandDescription(Description.of(description))
                .permission(permission)
                .handler(ctx -> {}));
    }

    private List<String> syntaxes() {
        return messages.stream()
                .filter(line -> line.startsWith("[orange]/"))
                .map(line -> line.substring("[orange]".length()).split("\\[lightgray]")[0])
                .toList();
    }

    @Test
    @DisplayName("The index lists what the sender has permission for, with descriptions")
    void index_isPermissionFiltered() {
        command("mute", "Mute a player.", "mod.mute");
        command("ban", "Ban a player.", "mod.ban");
        command("ping", "", "");

        new MindustryHelp<>(manager, 8).sendIndex(sender(null, "mod.mute"), 1);

        assertEquals(List.of(
                "[orange]-- Commands (page 1 of 1) --",
                "[orange]/mute[lightgray] - Mute a player.",
                "[orange]/ping"
        ), messages);
    }

    @Test
    @DisplayName("Compound permissions are evaluated by Cloud")
    void index_compoundPermissions() {
        manager.command(manager.commandBuilder("both")
                .permission(Permission.allOf(Permission.of("a"), Permission.of("b"))).handler(ctx -> {}));
        manager.command(manager.commandBuilder("either")
                .permission(Permission.anyOf(Permission.of("a"), Permission.of("b"))).handler(ctx -> {}));

        new MindustryHelp<>(manager, 8).sendIndex(sender(null, "a"), 1);

        assertEquals(List.of("/either"), syntaxes());
    }

    @Test
    @DisplayName("Player-only commands are hidden from the console, whatever the sender type")
    void index_playerOnly() {
        manager.command(manager.commandBuilder("home")
                .meta(MindustryCommandManager.PLAYER_ONLY, true).handler(ctx -> {}));
        manager.command(manager.commandBuilder("version").handler(ctx -> {}));
        MindustryHelp<AppSender> help = new MindustryHelp<>(manager, 8);

        help.sendIndex(sender(null), 1);
        assertEquals(List.of("/version"), syntaxes());

        messages.clear();
        help.sendIndex(sender(player()), 1);
        assertEquals(List.of("/home", "/version"), syntaxes());
    }

    @Test
    @DisplayName("Pages are cut at pageSize, with a footer while more follow")
    void index_pages() {
        for (String name : List.of("a", "b", "c", "d", "e")) {
            command(name, "", "");
        }
        MindustryHelp<AppSender> help = new MindustryHelp<>(manager, 2);

        help.sendIndex(sender(null), 1);
        assertEquals(List.of(
                "[orange]-- Commands (page 1 of 3) --",
                "[orange]/a",
                "[orange]/b",
                "[lightgray]Page 1 of 3; continue with page 2."
        ), messages);

        messages.clear();
        help.sendIndex(sender(null), 3);
        assertEquals(List.of("[orange]-- Commands (page 3 of 3) --", "[orange]/e"), messages);

        for (int invalid : new int[]{0, 4, -1}) {
            messages.clear();
            help.sendIndex(sender(null), invalid);
            assertEquals(List.of("[scarlet]Page " + invalid + " does not exist; there are 3."), messages);
        }
    }

    @Test
    @DisplayName("A sender who can use nothing is told so")
    void index_empty() {
        command("ban", "Ban a player.", "mod.ban");

        new MindustryHelp<>(manager, 8).sendIndex(sender(null), 1);

        assertEquals(List.of("[scarlet]There are no commands you can use."), messages);
        assertThrows(IllegalArgumentException.class, () -> new MindustryHelp<>(manager, 0));
    }

    @Test
    @DisplayName("Querying a command shows its usage, description and described arguments")
    void query_usage() {
        manager.command(manager.commandBuilder("mute", "m")
                .commandDescription(Description.of("Mute a player."))
                .required("player", StringParser.stringParser(), Description.of("Who to mute"))
                .optional("minutes", IntegerParser.integerParser())
                .handler(ctx -> {}));

        new MindustryHelp<>(manager, 8).sendQuery(sender(null), "mute", 1);

        assertEquals(List.of(
                "[orange]Usage: [white]/mute <player> [minutes]",
                "[lightgray]Mute a player.",
                "[white]  player[lightgray] - Who to mute"
        ), messages);
    }

    @Test
    @DisplayName("Querying a root with several variants lists the permitted ones")
    void query_variants() {
        manager.command(manager.commandBuilder("perm").literal("check").permission("perm.check").handler(ctx -> {}));
        manager.command(manager.commandBuilder("perm").literal("reload").permission("perm.reload").handler(ctx -> {}));
        manager.command(manager.commandBuilder("perm").literal("roles").handler(ctx -> {}));
        MindustryHelp<AppSender> help = new MindustryHelp<>(manager, 8);

        help.sendQuery(sender(null, "perm.check"), "perm", 1);
        assertEquals(List.of("/perm check", "/perm roles"), syntaxes());
        assertEquals("[orange]-- /perm (page 1 of 1) --", messages.get(0));

        messages.clear();
        help.sendIndex(sender(null, "perm.check"), 1);
        assertEquals(List.of("/perm check", "/perm roles"), syntaxes());
    }

    @Test
    @DisplayName("An unknown query and a command the sender may not use look the same")
    void query_noMatch() {
        command("ban", "Ban a player.", "mod.ban");
        MindustryHelp<AppSender> help = new MindustryHelp<>(manager, 8);

        help.sendQuery(sender(null), "nothing", 1);
        help.sendQuery(sender(null), "ban", 1);

        assertEquals(List.of(
                "[scarlet]No command matches '[white]nothing[]'.",
                "[scarlet]No command matches '[white]ban[]'."
        ), messages);
    }

    @Test
    @DisplayName("After a PREFIX collision help shows, and understands, the published name")
    void prefixedNames() {
        manager.setConflictStrategy(ConflictStrategy.PREFIX);
        manager.setCommandPrefix("xcore");
        command("sync", "Cloud sync.", "");
        MindustryHelp<AppSender> help = new MindustryHelp<>(manager, 8);

        help.sendIndex(sender(null), 1);
        assertEquals(List.of("/xcore:sync"), syntaxes());

        messages.clear();
        help.sendQuery(sender(null), "/xcore:sync", 1);
        assertEquals("[orange]Usage: [white]/xcore:sync", messages.get(0));
    }

    @Test
    @DisplayName("A root that was skipped is not offered")
    void skippedRoots() {
        manager.setConflictStrategy(ConflictStrategy.SKIP);
        command("sync", "Cloud sync.", "");
        command("ping", "", "");

        new MindustryHelp<>(manager, 8).sendIndex(sender(null), 1);

        assertEquals(List.of("/ping"), syntaxes());
    }

    @Test
    @DisplayName("A command with aliases is listed once")
    void aliases() {
        command("teleport", "", "", "tp", "tele");

        new MindustryHelp<>(manager, 8).sendIndex(sender(null), 1);

        assertEquals(List.of("/teleport"), syntaxes());
    }

    @Test
    @DisplayName("Arc commands appear only when asked for, filtered by their own predicate")
    void legacyCommands() {
        command("ping", "", "");
        MindustryHelp<AppSender> help = new MindustryHelp<>(manager, 8)
                .includeLegacyCommands((sender, legacy) -> !legacy.text.equals("sync"));

        help.sendIndex(sender(null), 1);
        assertEquals(List.of(
                "[orange]-- Commands (page 1 of 1) --",
                "[orange]/ping",
                "[orange]/t <message...>[lightgray] - Send a message only to your teammates."
        ), messages);

        messages.clear();
        help.sendQuery(sender(null), "sync", 1);
        assertEquals(List.of("[scarlet]No command matches '[white]sync[]'."), messages);
    }

    @Test
    @DisplayName("Translated captions are used; untranslated ones keep the defaults")
    void captions() {
        command("ping", "", "");
        manager.captionRegistry().registerProvider((caption, sender) ->
                caption.equals(MindustryCaptionKeys.HELP_TITLE) ? "Команды: <page> из <pages>" : null);

        new MindustryHelp<>(manager, 8).sendIndex(sender(null), 1);

        assertEquals(List.of("Команды: 1 из 1", "[orange]/ping"), messages);
    }
}
