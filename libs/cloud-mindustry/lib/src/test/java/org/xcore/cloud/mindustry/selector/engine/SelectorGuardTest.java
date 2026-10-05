package org.xcore.cloud.mindustry.selector.engine;

import arc.util.CommandHandler;
import org.incendo.cloud.Command;
import org.incendo.cloud.context.CommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.exception.SelectorDeniedException;
import org.xcore.cloud.mindustry.selector.parser.SelectorSyntaxParser;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectorGuardTest {

    private MindustryCommandManager<MindustrySender> manager;
    private Command<MindustrySender> command;

    @BeforeEach
    void setUp() {
        manager = MindustryCommandManager.create(new CommandHandler(""));
        command = manager.commandBuilder("probe")
                .required("target", stringParser())
                .meta(SelectorGuard.ALLOWED_SELECTORS_KEY, new SelectorKind[]{SelectorKind.SELF})
                .build();
    }

    private CommandContext<MindustrySender> context() {
        return new CommandContext<>(new MindustrySender.ConsoleSender(), manager);
    }

    private static TargetSelectorSpec spec(String input) {
        return SelectorSyntaxParser.parse(input);
    }

    @Test
    @DisplayName("A selector recorded for an argument of the command is enforced")
    void recordedSelector_isEnforced() {
        CommandContext<MindustrySender> ctx = context();
        ctx.createParsingContext(command.components().get(1));
        SelectorGuard.checkGuard(ctx, spec("@r"));

        assertEquals(1, SelectorGuard.selectorSpecs(command, ctx).size());
        assertThrows(SelectorDeniedException.class, () -> SelectorGuard.enforceAll(command, ctx));
    }

    @Test
    @DisplayName("A selector from an abandoned attempt at the argument is ignored")
    void abandonedAttempt_isIgnored() {
        CommandContext<MindustrySender> ctx = context();
        // First attempt parsed a selector, then the branch was abandoned...
        ctx.createParsingContext(command.components().get(1));
        SelectorGuard.checkGuard(ctx, spec("@r"));
        // ...and the argument was parsed again, without a selector, on the path that matched.
        ctx.createParsingContext(command.components().get(1));

        assertEquals(0, SelectorGuard.selectorSpecs(command, ctx).size());
        assertDoesNotThrow(() -> SelectorGuard.enforceAll(command, ctx));
    }

    @Test
    @DisplayName("A selector recorded for an argument the command does not have is ignored")
    void foreignArgument_isIgnored() {
        CommandContext<MindustrySender> ctx = context();
        Command<MindustrySender> other = manager.commandBuilder("other")
                .required("victim", stringParser())
                .build();
        ctx.createParsingContext(other.components().get(1));
        SelectorGuard.checkGuard(ctx, spec("@r"));

        assertDoesNotThrow(() -> SelectorGuard.enforceAll(command, ctx));
    }
}
