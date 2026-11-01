package org.xcore.cloud.mindustry;

import arc.struct.Seq;
import arc.util.CommandHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArcCommandRegistrationHandlerTest {

    private CommandHandler handler;
    private MindustryCommandManager<MindustrySender> manager;
    private List<String> executed;

    @BeforeEach
    void setUp() {
        handler = new CommandHandler("");
        executed = new ArrayList<>();
        vanilla("first");
        vanilla("help");
        vanilla("middle");
        vanilla("h");
        vanilla("last");
        manager = MindustryCommandManager.create(handler);
        manager.setConflictStrategy(ConflictStrategy.OVERRIDE);
    }

    private CommandHandler.Command vanilla(String name) {
        return handler.register(name, "vanilla " + name, args -> executed.add("vanilla:" + name));
    }

    private void cloud(String name, String... aliases) {
        manager.command(manager.commandBuilder(name, aliases).handler(ctx -> executed.add("cloud:" + name)));
    }

    private List<String> order() {
        List<String> names = new ArrayList<>();
        for (CommandHandler.Command command : handler.getCommandList()) names.add(command.text);
        return names;
    }

    private CommandHandler.Command find(String name) {
        return handler.getCommandList().find(c -> c.text.equals(name));
    }

    /** Every listed command is the one the handler would run for its name, and nothing is listed twice. */
    private void assertListAgreesWithMap() {
        Seq<CommandHandler.Command> list = handler.getCommandList();
        assertEquals(list.size, order().stream().distinct().count(), order().toString());
        for (CommandHandler.Command command : list) {
            executed.clear();
            handler.handleMessage(command.text);
            String owner = command instanceof MindustryCloudCommand ? "cloud:" : "vanilla:";
            assertEquals(1, executed.size(), command.text);
            assertTrue(executed.get(0).startsWith(owner), command.text + " ran " + executed);
        }
    }

    @Test
    @DisplayName("Deleting an overriding root puts the vanilla command back at its position")
    void override_isReversedOnDelete() {
        CommandHandler.Command vanillaHelp = find("help");
        List<String> before = order();

        cloud("help");
        assertInstanceOf(MindustryCloudCommand.class, find("help"));
        assertListAgreesWithMap();

        manager.deleteRootCommand("help");

        assertEquals(before, order());
        assertSame(vanillaHelp, find("help"));
        assertListAgreesWithMap();
    }

    @Test
    @DisplayName("Aliases are restored independently of the root")
    void override_restoresAliases() {
        CommandHandler.Command vanillaHelp = find("help");
        CommandHandler.Command vanillaH = find("h");
        List<String> before = order();

        cloud("help", "h");
        manager.deleteRootCommand("help");

        assertEquals(before, order());
        assertSame(vanillaHelp, find("help"));
        assertSame(vanillaH, find("h"));
        assertListAgreesWithMap();
    }

    @Test
    @DisplayName("A root that displaced nothing is simply removed")
    void noDisplacement_isRemoved() {
        List<String> before = order();

        cloud("fresh", "h");
        manager.deleteRootCommand("fresh");

        assertEquals(before, order());
        assertListAgreesWithMap();
    }

    @Test
    @DisplayName("A name re-registered by someone else is neither removed nor overwritten")
    void thirdPartyReplacement_isLeftAlone() {
        cloud("help");
        CommandHandler.Command thirdParty = handler.register("help", "third party", args -> executed.add("vanilla:help"));

        manager.deleteRootCommand("help");

        assertSame(thirdParty, find("help"));
        assertEquals(1, order().stream().filter("help"::equals).count());
        assertListAgreesWithMap();
    }

    @Test
    @DisplayName("The root can be registered and deleted repeatedly")
    void repeatedDeletion() {
        CommandHandler.Command vanillaHelp = find("help");
        List<String> before = order();

        cloud("help");
        manager.deleteRootCommand("help");
        cloud("help");
        assertInstanceOf(MindustryCloudCommand.class, find("help"));
        manager.deleteRootCommand("help");

        assertEquals(before, order());
        assertSame(vanillaHelp, find("help"));
    }

    @Test
    @DisplayName("FAIL on an alias takes back the root it had already published")
    void failOnAlias_rollsBack() {
        manager.setConflictStrategy(ConflictStrategy.FAIL);
        List<String> before = order();

        assertThrows(RuntimeException.class, () -> cloud("fresh", "h"));

        assertEquals(before, order());
        assertListAgreesWithMap();
    }
}
