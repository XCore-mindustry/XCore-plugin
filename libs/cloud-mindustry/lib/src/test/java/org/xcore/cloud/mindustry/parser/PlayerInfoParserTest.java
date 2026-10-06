package org.xcore.cloud.mindustry.parser;

import arc.Core;
import arc.Settings;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import mindustry.net.Administration;
import mindustry.net.Administration.PlayerInfo;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.parser.ParserDescriptor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlayerInfoParserTest {

    /** A sender type of the application's own: the parser has to find the console through the manager. */
    record AppSender(MindustrySender raw) {}

    private Administration admins;
    private MindustryCommandManager<AppSender> manager;
    private List<String> messages;
    private List<PlayerInfo> parsed;

    @BeforeAll
    static void initMindustry() {
        Vars.state = new GameState();
        Vars.headless = true;
        Vars.net = new mindustry.net.Net(null);
        if (Vars.content == null) {
            Vars.content = new ContentLoader();
        }
        if (Core.settings == null) {
            Core.settings = new Settings();
        }
    }

    @BeforeEach
    void setUp() {
        Groups.init();
        admins = new Administration();
        admins.playerInfo.clear();
        manager = new MindustryCommandManager<>(
                new CommandHandler(""),
                ExecutionCoordinator.simpleCoordinator(),
                SenderMapper.create(AppSender::new, AppSender::raw)
        );
        messages = new ArrayList<>();
        parsed = new ArrayList<>();
        manager.command(manager.commandBuilder("info")
                .required("target", ParserDescriptor.of(new PlayerInfoParser<>(() -> admins), PlayerInfo.class))
                .handler(ctx -> parsed.add(ctx.get("target"))));
    }

    private PlayerInfo record(String uuid, String ip, String... names) {
        PlayerInfo info = new PlayerInfo();
        info.id = uuid;
        info.lastIP = ip;
        info.ips.add(ip);
        info.names.addAll(names);
        info.lastName = names[names.length - 1];
        admins.playerInfo.put(uuid, info);
        return info;
    }

    private AppSender sender(Player player) {
        return new AppSender(new MindustrySender() {
            @Override public void sendMessage(String message) { messages.add(message); }
            @Override public String name() { return player != null ? player.plainName() : "Console"; }
            @Override public boolean isPlayer() { return player != null; }
            @Override public Player player() { return player; }
        });
    }

    private Player player(int id, String name) {
        Player p = Player.create();
        p.id = id;
        p.name = name;
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
    @DisplayName("A UUID finds the record, online or not")
    void byUuid() {
        PlayerInfo alice = record("uuid-alice", "10.0.0.1", "alice");

        run(sender(null), "info uuid-alice");

        assertEquals(List.of(alice), parsed);
    }

    @Test
    @DisplayName("Any name the player has used matches, with or without color tags and in any case")
    void byHistoricalName() {
        PlayerInfo alice = record("uuid-alice", "10.0.0.1", "[red]Alice", "wonderland");
        record("uuid-bob", "10.0.0.2", "bob");

        run(sender(null), "info alice");
        run(sender(null), "info [red]Alice");
        run(sender(null), "info WONDERLAND");

        assertEquals(List.of(alice, alice, alice), parsed);
        assertEquals(List.of(), messages);
    }

    @Test
    @DisplayName("A name shared by several records is an error, never a pick")
    void ambiguousName() {
        record("uuid-1", "10.0.0.1", "steve");
        record("uuid-2", "10.0.0.2", "[blue]steve");

        run(sender(null), "info steve");

        assertEquals(List.of(), parsed);
        assertEquals(List.of("[scarlet]'[white]steve[]' matches 2 player records; use a UUID instead."), messages);
    }

    @Test
    @DisplayName("Unknown input is reported as not found")
    void notFound() {
        record("uuid-alice", "10.0.0.1", "alice");

        run(sender(null), "info nobody");
        run(sender(null), "info #99");

        assertEquals(List.of(), parsed);
        assertEquals(List.of(
                "[scarlet]No player record matches '[white]nobody[]'.",
                "[scarlet]No player record matches '[white]#99[]'."
        ), messages);
    }

    @Test
    @DisplayName("The console can look a player up by IP")
    void byIp_console() {
        PlayerInfo alice = record("uuid-alice", "10.0.0.1", "alice");
        record("uuid-bob", "10.0.0.2", "bob");
        record("uuid-carol", "10.0.0.2", "carol");

        run(sender(null), "info 10.0.0.1");
        run(sender(null), "info 10.0.0.2");

        assertEquals(List.of(alice), parsed);
        assertEquals(List.of("[scarlet]'[white]10.0.0.2[]' matches 2 player records; use a UUID instead."), messages);
    }

    @Test
    @DisplayName("A player gets the same refusal for a known and an unknown IP")
    void byIp_player() {
        record("uuid-alice", "10.0.0.1", "alice");
        AppSender player = sender(player(1, "mallory"));

        run(player, "info 10.0.0.1");
        run(player, "info 10.9.9.9");

        assertEquals(List.of(), parsed);
        assertEquals(List.of(
                "[scarlet]Players can only be looked up by IP from the console.",
                "[scarlet]Players can only be looked up by IP from the console."
        ), messages);
    }

    @Test
    @DisplayName("#id names an online player only")
    void byEntityId() {
        Player online = player(7, "dave");
        PlayerInfo dave = record(online.uuid(), "10.0.0.4", "dave");

        run(sender(null), "info #7");

        assertEquals(List.of(dave), parsed);
    }

    @Test
    @DisplayName("Suggestions name online players, not the stored history")
    void suggestions() {
        record("uuid-alice", "10.0.0.1", "alice");
        player(7, "dave");
        player(8, "two words");

        List<String> suggestions = manager.suggestionFactory()
                .suggestImmediately(sender(null), "info ").list().stream()
                .map(suggestion -> suggestion.suggestion())
                .sorted()
                .toList();

        assertEquals(List.of("#8", "dave"), suggestions);
    }
}
