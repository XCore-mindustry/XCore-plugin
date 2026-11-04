package org.xcore.plugin.cloud;

import arc.util.CommandHandler;
import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ParserDescriptor;
import mindustry.net.Administration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.cloud.mindustry.selector.SelectorKind;
import org.xcore.cloud.mindustry.selector.annotation.AllowedSelectors;
import org.xcore.cloud.mindustry.selector.annotation.DenySelectors;
import org.xcore.plugin.cloud.config.CloudCaptionConfigurer;
import org.xcore.plugin.cloud.config.CloudExceptionConfigurer;
import org.xcore.plugin.cloud.config.CloudManagerFactory;
import org.xcore.plugin.cloud.config.CloudParserConfigurer;
import org.xcore.plugin.permission.PermissionService;
import org.xcore.plugin.permission.RemoteConsoleScope;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.localization.TranslatorLanguagesProvider;
import org.xcore.plugin.metrics.DefaultMetricsService;
import org.xcore.plugin.metrics.LocalMetricRegistry;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.session.SessionService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a player actually sees when a command fails, through the full exception configuration.
 */
class CloudExceptionHandlingIntegrationTest {

    private MindustryCommandManager<XCoreSender> manager;
    private AnnotationParser<XCoreSender> parser;
    private Player player;
    private XCoreSender sender;
    private Bundle bundle;
    private final List<String> consoleMessages = new ArrayList<>();
    private XCoreSender console;
    private final AtomicReference<RuntimeException> guardFailure = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        var sessionService = mock(SessionService.class);
        var bundle = mock(Bundle.class);
        when(bundle.format(any(), anyString(), anyMap())).thenAnswer(i -> "msg:" + i.getArgument(1));
        when(bundle.has(any(), anyString())).thenReturn(true);
        this.bundle = bundle;

        player = mock(Player.class);
        when(player.uuid()).thenReturn("test-uuid");
        when(player.plainName()).thenReturn("tester");
        sender = new XCoreSender(new MindustrySender.PlayerSender(player), bundle, () -> sessionService);
        console = new XCoreSender(new MindustrySender() {
            @Override public void sendMessage(String message) { consoleMessages.add(message); }
            @Override public String name() { return "Console"; }
            @Override public boolean isPlayer() { return false; }
            @Override public Player player() { return null; }
        }, bundle, () -> sessionService);

        var metrics = new DefaultMetricsService(new LocalMetricRegistry(), new TomlXcoreConfig());
        var factory = new CloudManagerFactory(bundle, () -> sessionService, metrics,
                new PermissionService(), new RemoteConsoleScope(), new CloudCaptionConfigurer(bundle));
        manager = factory.createManager(new CommandHandler(""));
        new CloudParserConfigurer(mock(TimeService.class), mock(TranslatorLanguagesProvider.class)).configure(manager);
        new CloudExceptionConfigurer(bundle, () -> sessionService).configure(manager);
        manager.registerCommandPostProcessor(ctx -> {
            RuntimeException failure = guardFailure.get();
            if (failure != null) {
                throw failure;
            }
        });

        parser = new AnnotationParser<>(manager, XCoreSender.class);
        manager.registerMindustryAnnotations(parser);
    }

    private List<String> run(String input) {
        try {
            manager.commandExecutor().executeCommand(sender, input).join();
        } catch (Exception ignored) {
            // the future completes exceptionally even after the failure was reported
        }
        var captor = ArgumentCaptor.forClass(String.class);
        verify(player, atLeast(0)).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    private List<String> runAsConsole(String input) {
        try {
            manager.commandExecutor().executeCommand(console, input).join();
        } catch (Exception ignored) {
            // the future completes exceptionally even after the failure was reported
        }
        return consoleMessages;
    }

    /** Every argument map the bundle was asked to render. */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> renderedArgs() {
        var captor = ArgumentCaptor.forClass(Map.class);
        verify(bundle, atLeast(0)).format(any(), anyString(), captor.capture());
        return (List<Map<String, Object>>) (List<?>) captor.getAllValues();
    }

    @Test
    @DisplayName("guard exceptions from postprocessors are localized, not shown as raw keys")
    void guardException_isLocalized() {
        parser.parse(new Object() {
            @Command("chat")
            public void chat(XCoreSender sender) {
            }
        });
        guardFailure.set(new XCoreCommandException("error-playtime-requirement"));

        assertThat(run("chat")).containsExactly("msg:error-playtime-requirement");
    }

    @Test
    @DisplayName("silent guard exceptions (mute) send nothing")
    void silentGuardException_sendsNothing() {
        parser.parse(new Object() {
            @Command("chat")
            public void chat(XCoreSender sender) {
            }
        });
        guardFailure.set(new XCoreCommandException(true));

        run("chat");

        verify(player, never()).sendMessage(anyString());
    }

    @Test
    @DisplayName("internal handler failures show the generic error, never the exception text")
    void internalFailure_isNotLeaked() {
        parser.parse(new Object() {
            @Command("boom")
            public void boom(XCoreSender sender) {
                throw new IllegalStateException("secret internal detail");
            }
        });

        assertThat(run("boom")).containsExactly("msg:error-internal");
    }

    @Test
    @DisplayName("selectors are refused on a @DenySelectors player parameter")
    void playerParameter_honoursDenySelectors() {
        var handled = new AtomicBoolean();
        parser.parse(new Object() {
            @Command("kick <target>")
            public void kick(XCoreSender sender, @Argument("target") @DenySelectors Player target) {
                handled.set(true);
            }
        });

        assertThat(run("kick @a")).containsExactly("msg:argument-parse-failure-selector-denied");
        assertThat(handled).isFalse();
    }

    @Test
    @DisplayName("a selector kind outside @AllowedSelectors gets its own localized message")
    void playerParameter_honoursAllowedSelectors() {
        parser.parse(new Object() {
            @Command("look <target>")
            public void look(XCoreSender sender, @Argument("target") @AllowedSelectors(SelectorKind.SELF) Player target) {
            }
        });

        assertThat(run("look @r")).containsExactly("msg:argument-parse-failure-selector-kind-not-allowed");
    }

    @Test
    @DisplayName("a parser failing with a plain exception shows the generic error, never its message")
    void plainParserFailure_isNotLeaked() {
        manager.command(manager.commandBuilder("lookup")
                .required("what", ParserDescriptor.of(
                        (ctx, input) -> {
                            input.readString();
                            return ArgumentParseResult.failure(new IllegalStateException("secret internal detail"));
                        }, String.class))
                .handler(ctx -> {
                }));

        assertThat(run("lookup x")).containsExactly("msg:error-argument-parse-generic");
        assertThat(renderedArgs()).allSatisfy(args -> assertThat(args.values()).noneMatch(
                value -> String.valueOf(value).contains("secret")));
    }

    @Test
    @DisplayName("a parser caption the bundle does not translate falls back to its default text, not the raw key")
    void untranslatedParserCaption_usesDefaultText() {
        when(bundle.has(any(), org.mockito.ArgumentMatchers.eq("argument-parse-failure-player-info"))).thenReturn(false);
        parser.parse(new Object() {
            @Command("seen <who>")
            public void seen(XCoreSender sender, @Argument("who") Administration.PlayerInfo who) {
            }
        });

        assertThat(run("seen nobody"))
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("No player record matches")
                        .doesNotContain("argument-parse-failure-player-info"));
    }

    @Test
    @DisplayName("internal failures of console commands are localized and do not echo the exception text")
    void consoleInternalFailure_isNotLeaked() {
        parser.parse(new Object() {
            @Command("boom")
            public void boom(XCoreSender sender) {
                throw new IllegalStateException("secret internal detail");
            }
        });

        assertThat(runAsConsole("boom")).containsExactly("msg:error-internal");
    }

    @Test
    @DisplayName("console syntax errors are localized like a player's")
    void consoleInvalidSyntax_isLocalized() {
        parser.parse(new Object() {
            @Command("sum <a> <b>")
            public void sum(XCoreSender sender, @Argument("a") int a, @Argument("b") int b) {
            }
        });

        assertThat(runAsConsole("sum 1")).containsExactly("msg:error-invalid-syntax");
    }

    @Test
    @DisplayName("Cloud caption keys map to dashed bundle keys")
    void bundleKey_replacesDotsAndUnderscores() {
        assertThat(CloudCaptionConfigurer.bundleKey(Caption.of("exception.invalid_argument")))
                .isEqualTo("exception-invalid-argument");
        assertThat(CloudCaptionConfigurer.bundleKey(Caption.of("argument.parse.failure.selector.no_such_target")))
                .isEqualTo("argument-parse-failure-selector-no-such-target");
    }

    @Test
    @DisplayName("a player name with spaces is read as typed or in quotes")
    void playerNameWithSpaces() {
        mindustry.gen.Groups.init();
        Player bob = Player.create();
        bob.id = 7;
        bob.name = "[gold]Big Bob";
        bob.add();
        var targets = new ArrayList<String>();
        parser.parse(new Object() {
            @Command("look <target> [note]")
            public void look(XCoreSender sender, @Argument("target") Player target, @Argument("note") String note) {
                targets.add(target.plainName() + "|" + note);
            }
        });

        Player hash = Player.create();
        hash.id = 8;
        hash.name = "#Big Bob";
        hash.add();

        runAsConsole("look Big Bob now");
        runAsConsole("look \"big bob\"");
        runAsConsole("look #Big Bob");
        runAsConsole("look #7");

        assertThat(targets).containsExactly("Big Bob|now", "Big Bob|null", "#Big Bob|null", "Big Bob|null");
        assertThat(consoleMessages).isEmpty();
    }
}
