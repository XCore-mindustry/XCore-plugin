package org.xcore.plugin.cloud;

import arc.util.CommandHandler;
import com.ospx.flubundle.Bundle;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.key.CloudKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.annotation.PlayTimeLimit;
import org.xcore.plugin.cloud.annotation.RequiresMuteCheck;
import org.xcore.plugin.cloud.annotation.RequiresPlayTime;
import org.xcore.plugin.cloud.config.CloudManagerFactory;
import org.xcore.plugin.cloud.config.CloudPermissionPolicy;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.metrics.DefaultMetricsService;
import org.xcore.plugin.metrics.LocalMetricRegistry;
import org.xcore.plugin.metrics.XcoreMetrics;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.session.SessionService;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying that @RequiresMuteCheck prevents command handler execution
 * when a player is muted, using the actual Cloud command pipeline.
 */
class RequiresMuteCheckIntegrationTest {

    private static final CloudKey<Boolean> REQUIRES_MUTE_CHECK_META =
            CloudKey.of("xcore.requiresMuteCheck", Boolean.class);
    private static final CloudKey<PlayTimeLimit> REQUIRES_PLAY_TIME_META =
            CloudKey.of("xcore.requiresPlayTime", PlayTimeLimit.class);

    private MindustryCommandManager<XCoreSender> manager;
    private AnnotationParser<XCoreSender> parser;
    private SecurityService securityService;
    private mindustry.gen.Player player;
    private XCoreSender sender;
    private LocalMetricRegistry registry;

    @BeforeEach
    void setUp() {
        securityService = mock(SecurityService.class);
        var sessionService = mock(SessionService.class);
        var bundle = mock(Bundle.class);

        player = mock(mindustry.gen.Player.class);
        when(player.uuid()).thenReturn("test-uuid");
        player.admin = false;

        MindustrySender rawSender = new MindustrySender.PlayerSender(player);
        sender = new XCoreSender(rawSender, bundle, () -> sessionService);

        var config = new TomlXcoreConfig();
        config.telemetry.enabled = true;
        registry = new LocalMetricRegistry();
        var metricsService = new DefaultMetricsService(registry, config);

        CommandHandler handler = new CommandHandler("");
        CloudManagerFactory factory = new CloudManagerFactory(
                bundle,
                () -> sessionService,
                metricsService,
                new CloudPermissionPolicy(),
                mock(org.xcore.plugin.cloud.config.CloudCaptionConfigurer.class)
        );
        manager = factory.createManager(handler);

        // Mirror CloudService.configureAnnotationGuards
        parser = new AnnotationParser<>(manager, XCoreSender.class);
        parser.registerBuilderModifier(RequiresMuteCheck.class,
                (_, builder) -> builder.meta(REQUIRES_MUTE_CHECK_META, true));
        parser.registerBuilderModifier(RequiresPlayTime.class,
                (annotation, builder) -> builder.meta(REQUIRES_PLAY_TIME_META, annotation.value()));

        // Mirror CloudService.createManager mute post-processor
        manager.registerCommandPostProcessor(context -> {
            var commandMeta = context.command().commandMeta();
            XCoreSender s = context.commandContext().sender();

            if (commandMeta.getOrDefault(REQUIRES_MUTE_CHECK_META, false)
                    && s.isPlayer()
                    && securityService.isMuted(s.player())) {
                throw new XCoreCommandException(true);
            }
        });
    }

    @Test
    @DisplayName("@RequiresMuteCheck: handler is NOT invoked when player is muted")
    void muteCheck_blocksHandler_whenMuted() throws Exception {
        var handlerCalled = new AtomicBoolean(false);

        parser.parse(new Object() {
            @RequiresMuteCheck
            @Command("testchat <message>")
            public void handle(XCoreSender sender, @Argument("message") String message) {
                handlerCalled.set(true);
            }
        });

        when(securityService.isMuted(player)).thenReturn(true);

        // XCoreCommandException is thrown from the post-processor and surfaces as
        // ExecutionException wrapping PipelineException wrapping XCoreCommandException.
        // What matters is: the handler body was NOT entered.
        try {
            manager.commandExecutor().executeCommand(sender, "testchat hello").toCompletableFuture().get();
        } catch (java.util.concurrent.ExecutionException ignored) {
            // expected: the mute post-processor threw XCoreCommandException
        }

        assertThat(handlerCalled).isFalse();
        verify(securityService).isMuted(player);

        var samples = registry.snapshot();
        assertThat(sample(samples, XcoreMetrics.COMMANDS_TOTAL.name(), "testchat", "player", "blocked").value()).isEqualTo(1.0d);
        assertThat(sample(samples, XcoreMetrics.COMMAND_DURATION_SECONDS.name(), "testchat", "player").count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("@RequiresMuteCheck: handler IS invoked when player is not muted")
    void muteCheck_allowsHandler_whenNotMuted() throws Exception {
        var handlerCalled = new AtomicBoolean(false);

        parser.parse(new Object() {
            @RequiresMuteCheck
            @Command("testchat2 <message>")
            public void handle(XCoreSender sender, @Argument("message") String message) {
                handlerCalled.set(true);
            }
        });

        when(securityService.isMuted(player)).thenReturn(false);

        manager.commandExecutor().executeCommand(sender, "testchat2 hello").toCompletableFuture().get();

        assertThat(handlerCalled).isTrue();
    }

    @Test
    @DisplayName("No @RequiresMuteCheck: handler IS invoked regardless of mute status")
    void noMuteAnnotation_allowsHandler_evenWhenMuted() throws Exception {
        var handlerCalled = new AtomicBoolean(false);

        parser.parse(new Object() {
            @Command("testnochat <message>")
            public void handle(XCoreSender sender, @Argument("message") String message) {
                handlerCalled.set(true);
            }
        });

        when(securityService.isMuted(player)).thenReturn(true);

        manager.commandExecutor().executeCommand(sender, "testnochat hello").toCompletableFuture().get();

        assertThat(handlerCalled).isTrue();
        verify(securityService, never()).isMuted(any());
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
