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
import org.xcore.plugin.cloud.config.CloudManagerFactory;
import org.xcore.plugin.cloud.config.CloudPermissionPolicy;
import org.xcore.plugin.cloud.config.DisabledCommandPolicy;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.metrics.DefaultMetricsService;
import org.xcore.plugin.metrics.LocalMetricRegistry;
import org.xcore.plugin.metrics.XcoreMetrics;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.session.SessionService;

import java.util.List;
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
    private XCoreSender serverSender;
    private LocalMetricRegistry registry;

    @BeforeEach
    void setUp() {
        var securityService = mock(SecurityService.class);
        var sessionService = mock(SessionService.class);
        var bundle = mock(Bundle.class);
        var secretsConfig = new TomlSecretsConfig();
        var config = new TomlXcoreConfig();
        config.runtime.disabledCommands = Set.of("test foo", "root");
        config.telemetry.enabled = true;

        registry = new LocalMetricRegistry();
        var metricsService = new DefaultMetricsService(registry, config);

        var player = mock(mindustry.gen.Player.class);
        when(player.uuid()).thenReturn("test-uuid");
        player.admin = false;

        MindustrySender rawSender = new MindustrySender.PlayerSender(player);
        sender = new XCoreSender(rawSender, bundle, () -> sessionService);
        serverSender = new XCoreSender(new MindustrySender.ConsoleSender(), bundle, () -> sessionService);

        CommandHandler handler = new CommandHandler("");
        SenderMapper<MindustrySender, XCoreSender> senderMapper = SenderMapper.create(
                base -> new XCoreSender(base, bundle, () -> sessionService),
                XCoreSender::getHandle
        );

        CloudManagerFactory factory = new CloudManagerFactory(
                bundle,
                () -> sessionService,
                metricsService,
                new CloudPermissionPolicy(),
                mock(org.xcore.plugin.cloud.config.CloudCaptionConfigurer.class)
        );
        manager = factory.createManager(handler);
        parser = new AnnotationParser<>(manager, XCoreSender.class);

        DisabledCommandPolicy policy = new DisabledCommandPolicy(config);
        CloudGuardConfigurer guardConfigurer = new CloudGuardConfigurer(
                () -> securityService,
                () -> sessionService,
                secretsConfig,
                policy,
                metricsService
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

        var samples = registry.snapshot();
        assertThat(sample(samples, XcoreMetrics.COMMANDS_TOTAL.name(), "test foo", "player", "blocked").value()).isEqualTo(1.0d);
        var duration = sample(samples, XcoreMetrics.COMMAND_DURATION_SECONDS.name(), "test foo", "player");
        assertThat(duration.count()).isEqualTo(1L);
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

    @Test
    @DisplayName("command metrics are recorded for success and error paths")
    void commandMetrics_recordSuccessAndErrorPaths() throws Exception {
        parser.parse(new Object() {
            @Command("metrics-ok <message>")
            public void ok(XCoreSender sender, @Argument("message") String message) {
            }

            @Command("metrics-fail <message>")
            public void fail(XCoreSender sender, @Argument("message") String message) {
                throw new IllegalStateException("boom");
            }
        });

        manager.commandExecutor().executeCommand(sender, "metrics-ok hello").toCompletableFuture().get();

        try {
            manager.commandExecutor().executeCommand(sender, "metrics-fail hello").toCompletableFuture().get();
        } catch (ExecutionException ignored) {
            // expected exceptional completion from handler failure
        }

        var samples = registry.snapshot();

        assertThat(sample(samples, XcoreMetrics.COMMANDS_TOTAL.name(), "metrics-ok", "player", "success").value()).isEqualTo(1.0d);
        assertThat(sample(samples, XcoreMetrics.COMMANDS_TOTAL.name(), "metrics-fail", "player", "error").value()).isEqualTo(1.0d);

        var okDuration = sample(samples, XcoreMetrics.COMMAND_DURATION_SECONDS.name(), "metrics-ok", "player");
        assertThat(okDuration.count()).isEqualTo(1L);
        assertThat(okDuration.sum()).isNotNull().isGreaterThanOrEqualTo(0.0d);

        var failDuration = sample(samples, XcoreMetrics.COMMAND_DURATION_SECONDS.name(), "metrics-fail", "player");
        assertThat(failDuration.count()).isEqualTo(1L);
        assertThat(failDuration.sum()).isNotNull().isGreaterThanOrEqualTo(0.0d);
    }

    @Test
    @DisplayName("command metrics record server source for console commands")
    void commandMetrics_recordServerSourceForConsoleCommands() throws Exception {
        parser.parse(new Object() {
            @Command("server-metrics")
            public void handle(XCoreSender sender) {
            }
        });

        manager.commandExecutor().executeCommand(serverSender, "server-metrics").toCompletableFuture().get();

        var samples = registry.snapshot();
        assertThat(sample(samples, XcoreMetrics.COMMANDS_TOTAL.name(), "server-metrics", "server", "success").value()).isEqualTo(1.0d);

        var duration = sample(samples, XcoreMetrics.COMMAND_DURATION_SECONDS.name(), "server-metrics", "server");
        assertThat(duration.count()).isEqualTo(1L);
        assertThat(duration.sum()).isNotNull().isGreaterThanOrEqualTo(0.0d);
    }

    private org.xcore.protocol.generated.shared.MetricSampleV1 sample(List<org.xcore.protocol.generated.shared.MetricSampleV1> samples,
                                                                      String name,
                                                                      String command,
                                                                      String source) {
        return samples.stream()
                .filter(sample -> sample.name().equals(name))
                .filter(sample -> command.equals(sample.labels().get("command")))
                .filter(sample -> source.equals(sample.labels().get("source")))
                .findFirst()
                .orElseThrow();
    }

    private org.xcore.protocol.generated.shared.MetricSampleV1 sample(List<org.xcore.protocol.generated.shared.MetricSampleV1> samples,
                                                                      String name,
                                                                      String command,
                                                                      String source,
                                                                      String result) {
        return samples.stream()
                .filter(sample -> sample.name().equals(name))
                .filter(sample -> command.equals(sample.labels().get("command")))
                .filter(sample -> source.equals(sample.labels().get("source")))
                .filter(sample -> result.equals(sample.labels().get("result")))
                .findFirst()
                .orElseThrow();
    }
}
