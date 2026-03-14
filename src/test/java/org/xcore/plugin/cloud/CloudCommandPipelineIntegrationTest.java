package org.xcore.plugin.cloud;

import arc.util.CommandHandler;
import com.ospx.flubundle.Bundle;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.config.CloudGuardConfigurer;
import org.xcore.plugin.cloud.config.DisabledCommandPolicy;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.session.SessionService;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudCommandPipelineIntegrationTest {

    private MindustryCommandManager<XCoreSender> manager;
    private AnnotationParser<XCoreSender> parser;
    private XCoreSender sender;

    @BeforeEach
    void setUp() {
        var securityService = mock(SecurityService.class);
        var sessionService = mock(SessionService.class);
        var bundle = mock(Bundle.class);
        var globalConfig = new GlobalConfig();
        var config = new Config();
        config.disabledCommands = Set.of("test foo", "root");

        var player = mock(mindustry.gen.Player.class);
        when(player.uuid()).thenReturn("test-uuid");
        player.admin = false;

        MindustrySender rawSender = new MindustrySender.PlayerSender(player);
        sender = new XCoreSender(rawSender, bundle, () -> sessionService);

        CommandHandler handler = new CommandHandler("");
        SenderMapper<MindustrySender, XCoreSender> senderMapper = SenderMapper.create(
                base -> new XCoreSender(base, bundle, () -> sessionService),
                XCoreSender::getHandle
        );

        manager = new MindustryCommandManager<>(handler, ExecutionCoordinator.simpleCoordinator(), senderMapper);
        parser = new AnnotationParser<>(manager, XCoreSender.class);

        DisabledCommandPolicy policy = new DisabledCommandPolicy(config);
        CloudGuardConfigurer guardConfigurer = new CloudGuardConfigurer(
                () -> securityService,
                () -> sessionService,
                globalConfig,
                policy
        );

        guardConfigurer.configure(manager, commandName -> {
            throw new org.xcore.plugin.cloud.exception.XCoreCommandException("error-command-disabled", java.util.Map.of("command", commandName));
        });
    }

    @Test
    @DisplayName("disabled literal path is blocked before handler execution")
    void disabledLiteralPath_blocksHandler() {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);

        parser.parse(new Object() {
            @Command("test foo <message>")
            public void handle(XCoreSender sender, @Argument("message") String message) {
                handlerCalled.set(true);
            }
        });

        try {
            manager.commandExecutor().executeCommand(sender, "test foo hello").toCompletableFuture().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (ExecutionException ignored) {
            // expected: disabled command pipeline path
        }

        assertThat(handlerCalled).isFalse();
    }

    @Test
    @DisplayName("disabled root path blocks all subcommands")
    void disabledRootPath_blocksSubcommand() {
        AtomicBoolean handlerCalled = new AtomicBoolean(false);

        parser.parse(new Object() {
            @Command("root child <message>")
            public void handle(XCoreSender sender, @Argument("message") String message) {
                handlerCalled.set(true);
            }
        });

        try {
            manager.commandExecutor().executeCommand(sender, "root child hello").toCompletableFuture().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        } catch (ExecutionException ignored) {
            // expected: disabled command pipeline path
        }

        assertThat(handlerCalled).isFalse();
    }
}
