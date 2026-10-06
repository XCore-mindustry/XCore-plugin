package org.xcore.plugin.cloud;

import arc.util.CommandHandler;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Inject;
import mindustry.gen.Player;
import org.incendo.cloud.Command;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.config.CloudCaptionConfigurer;
import org.xcore.plugin.cloud.config.CloudManagerFactory;
import org.xcore.plugin.cloud.config.CloudParserConfigurer;
import org.xcore.plugin.cloud.config.CommandPermissions;
import org.xcore.plugin.command.controller.client.AuthController;
import org.xcore.plugin.command.controller.client.BadgeController;
import org.xcore.plugin.command.controller.client.BroadcastController;
import org.xcore.plugin.command.controller.client.EntityAdminController;
import org.xcore.plugin.command.controller.client.EventController;
import org.xcore.plugin.command.controller.client.HelpController;
import org.xcore.plugin.command.controller.client.InformationController;
import org.xcore.plugin.command.controller.client.MapController;
import org.xcore.plugin.command.controller.client.ModerationController;
import org.xcore.plugin.command.controller.client.PlayerController;
import org.xcore.plugin.command.controller.client.PrivateMessageController;
import org.xcore.plugin.command.controller.client.SeasonController;
import org.xcore.plugin.command.controller.client.ServerNavigationController;
import org.xcore.plugin.command.controller.client.SocialController;
import org.xcore.plugin.command.controller.client.TeleportController;
import org.xcore.plugin.command.controller.client.VoteController;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.permission.PermissionService;
import org.xcore.plugin.permission.RemoteConsoleScope;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.session.SessionService;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The player commands as the plugin registers them, checked against who could run each of them
 * before permission nodes: an admin everything, a player everything but the staff commands.
 */
class CommandPermissionFamiliesTest {

    private static final List<Class<?>> CLIENT_CONTROLLERS = List.of(
            AuthController.class, BadgeController.class, BroadcastController.class, EntityAdminController.class,
            EventController.class, HelpController.class, InformationController.class, MapController.class,
            ModerationController.class, PlayerController.class, PrivateMessageController.class,
            SeasonController.class, ServerNavigationController.class, SocialController.class,
            TeleportController.class, VoteController.class
    );

    /** The commands that asked for "admin" or an "xcore.admin.*" permission before. */
    private static final Set<String> STAFF_COMMANDS = Set.of(
            "ban", "unban", "mute", "unmute", "audit",
            "tp", "bring", "tpto", "tppos",
            "heal", "kill", "killunits",
            "alert", "toast", "announcement",
            "set-team", "artv", "avnw"
    );

    private MindustryCommandManager<XCoreSender> manager;
    private AnnotationParser<XCoreSender> parser;
    private SessionService sessionService;
    private Bundle bundle;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        bundle = mock(Bundle.class);
        rebuild(new RemoteConsoleScope());
    }

    private void rebuild(RemoteConsoleScope remoteConsole) {
        CloudManagerFactory factory = new CloudManagerFactory(
                bundle,
                () -> sessionService,
                mock(MetricsService.class),
                new PermissionService(() -> sessionService),
                remoteConsole,
                mock(CloudCaptionConfigurer.class)
        );
        manager = factory.createManager(new CommandHandler("/"));
        new CloudParserConfigurer(mock(TimeService.class), mock(RETURNS_DEEP_STUBS)).configure(manager);
        parser = new AnnotationParser<>(manager, XCoreSender.class);
        manager.registerMindustryAnnotations(parser);

        for (Class<?> controller : CLIENT_CONTROLLERS) {
            try {
                parser.parse(instantiate(controller));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** The controllers only have to exist for their annotations to be read, so every dependency is a mock. */
    private static Object instantiate(Class<?> type) throws ReflectiveOperationException {
        Constructor<?> chosen = null;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.isAnnotationPresent(Inject.class) || chosen == null) {
                chosen = constructor;
            }
            if (constructor.isAnnotationPresent(Inject.class)) {
                break;
            }
        }
        Object[] arguments = new Object[chosen.getParameterCount()];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = mock(chosen.getParameterTypes()[i]);
        }
        chosen.setAccessible(true);
        return chosen.newInstance(arguments);
    }

    private XCoreSender player(boolean admin) {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn(admin ? "admin-uuid" : "player-uuid");
        player.admin = admin;
        return new XCoreSender(new MindustrySender.PlayerSender(player), bundle, () -> sessionService);
    }

    private Set<String> runnableBy(XCoreSender sender) {
        Set<String> names = new TreeSet<>();
        for (Command<XCoreSender> command : manager.commands()) {
            if (manager.testPermission(sender, command.commandPermission()).allowed()) {
                names.add(command.rootComponent().name());
            }
        }
        return names;
    }

    private Set<String> allCommands() {
        Set<String> names = new TreeSet<>();
        manager.commands().forEach(command -> names.add(command.rootComponent().name()));
        return names;
    }

    @Test
    @DisplayName("An admin can run every player command")
    void admin() {
        assertThat(allCommands()).containsAll(STAFF_COMMANDS);
        assertThat(runnableBy(player(true))).isEqualTo(allCommands());
    }

    @Test
    @DisplayName("A player can run everything but the staff commands")
    void regularPlayer() {
        Set<String> expected = allCommands();
        expected.removeAll(STAFF_COMMANDS);

        assertThat(runnableBy(player(false))).isEqualTo(expected);
    }

    @Test
    @DisplayName("Every staff command has a node of its own family rather than a shared 'admin'")
    void nodesPerCommand() {
        for (Command<XCoreSender> command : manager.commands()) {
            Set<String> nodes = CommandPermissions.nodes(command.commandPermission());
            assertThat(nodes).as(command.rootComponent().name()).doesNotContain(PermissionNodes.LEGACY_ADMIN);
            assertThat(CommandPermissions.isRestricted(command))
                    .as(command.rootComponent().name())
                    .isEqualTo(STAFF_COMMANDS.contains(command.rootComponent().name()));
        }
    }

    @Test
    @DisplayName("The registered commands pass the startup check")
    void registeredCommandsAreDeclared() {
        CommandPermissions.verifyDeclared(manager.commands());
    }

    @Test
    @DisplayName("A typo in a permission stops startup and names the command")
    void typoStopsStartup() {
        parser.parse(new Object() {
            @Permission("xcore.moderation.bann")
            @org.incendo.cloud.annotations.Command("typo-command")
            public void handle(XCoreSender sender) {
            }
        });

        assertThatThrownBy(() -> CommandPermissions.verifyDeclared(manager.commands()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'xcore.moderation.bann' on /typo-command");
    }

    @Test
    @DisplayName("A command with a typo in its permission runs for nobody, not even an admin")
    void typoIsDenied() {
        parser.parse(new Object() {
            @Permission("xcore.moderation.bann")
            @org.incendo.cloud.annotations.Command("typo-command")
            public void handle(XCoreSender sender) {
            }
        });

        assertThat(runnableBy(player(true))).doesNotContain("typo-command");
    }

    /** What a console command saw: one entry per run, naming the console it came from. */
    private final List<String> consoleRuns = new java.util.ArrayList<>();

    private void registerConsoleCommands() {
        parser.parse(new Object() {
            @Permission(PermissionNodes.PERMISSIONS_MANAGE)
            @org.incendo.cloud.annotations.Command("console-only")
            public void consoleOnly(XCoreSender sender) {
                consoleRuns.add("console-only by " + sender.actor().auditName());
            }

            @org.incendo.cloud.annotations.Command("console-open")
            public void open(XCoreSender sender) {
                consoleRuns.add("console-open by " + sender.actor().auditName());
            }
        });
    }

    @Test
    @DisplayName("The local console runs console-only commands")
    void localConsole() {
        registerConsoleCommands();

        manager.commandHandler().handleMessage("/console-only");
        manager.commandHandler().handleMessage("/console-open");

        assertThat(consoleRuns).containsExactly("console-only by console", "console-open by console");
    }

    @Test
    @DisplayName("A command relayed by gcmd runs as before, except the console-only ones, and knows it is remote")
    void remoteConsole() {
        RemoteConsoleScope scope = new RemoteConsoleScope();
        rebuild(scope);
        registerConsoleCommands();

        scope.run("hub", () -> {
            manager.commandHandler().handleMessage("/console-only");
            manager.commandHandler().handleMessage("/console-open");
        });
        manager.commandHandler().handleMessage("/console-open");

        assertThat(consoleRuns).containsExactly("console-open by remote-console@hub", "console-open by console");
    }

    @Test
    @DisplayName("An admin cannot run a console-only command")
    void consoleOnlyIsNotForAdmins() {
        registerConsoleCommands();

        assertThat(runnableBy(player(true))).contains("console-open").doesNotContain("console-only");
    }
}
