package org.xcore.plugin.cloud.config;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.incendo.cloud.key.CloudKey;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.PlayTimeLimit;
import org.xcore.plugin.cloud.exception.XCoreCommandException;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.service.SecurityService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class CloudGuardConfigurer {
    private static final CloudKey<Boolean> REQUIRES_MUTE_CHECK_META = CloudKey.of("xcore.requiresMuteCheck", Boolean.class);
    private static final CloudKey<PlayTimeLimit> REQUIRES_PLAY_TIME_META = CloudKey.of("xcore.requiresPlayTime", PlayTimeLimit.class);
    static final CloudKey<String> TELEMETRY_COMMAND_NAME = CloudKey.of("xcore.telemetryCommandName", String.class);

    private final Provider<SecurityService> securityService;
    private final Provider<SessionService> sessionService;
    private final TomlSecretsConfig secretsConfig;
    private final DisabledCommandPolicy disabledCommandPolicy;
    private final MetricsService metricsService;

    @Inject
    public CloudGuardConfigurer(Provider<SecurityService> securityService,
                                Provider<SessionService> sessionService,
                                TomlSecretsConfig secretsConfig,
                                DisabledCommandPolicy disabledCommandPolicy,
                                MetricsService metricsService) {
        this.securityService = securityService;
        this.sessionService = sessionService;
        this.secretsConfig = secretsConfig;
        this.disabledCommandPolicy = disabledCommandPolicy;
        this.metricsService = metricsService;
    }

    public void configure(MindustryCommandManager<XCoreSender> manager,
                          java.util.function.Consumer<String> disabledCommandThrower) {
        manager.registerCommandPreProcessor(context -> {
            String disabledCommand = disabledCommandPolicy.disabledCommandKey(context.commandInput().remainingInput());
            if (disabledCommand != null) {
                context.commandContext().store(TELEMETRY_COMMAND_NAME, disabledCommand);
                CommandTelemetryRecorder.record(metricsService, context.commandContext().sender(), disabledCommand, "blocked", 0.0d);
                disabledCommandThrower.accept(disabledCommand);
            }
        });

        manager.registerCommandPostProcessor(context -> {
            String disabledCommand = disabledCommandPolicy.disabledCommandKeyFromCommand(context.command());
            if (disabledCommand == null) {
                return;
            }
            context.commandContext().store(TELEMETRY_COMMAND_NAME, disabledCommand);
            disabledCommandThrower.accept(disabledCommand);
        });

        manager.registerCommandPostProcessor(context -> {
            var commandMeta = context.command().commandMeta();
            XCoreSender sender = context.commandContext().sender();

            if (commandMeta.getOrDefault(REQUIRES_MUTE_CHECK_META, false)
                    && sender.isPlayer()
                    && securityService.get().isMuted(sender.player())) {
                throw new XCoreCommandException(true);
            }

            var playTimeLimit = commandMeta.optional(REQUIRES_PLAY_TIME_META).orElse(null);
            if (playTimeLimit == null || !sender.isPlayer()) {
                return;
            }

            Player player = sender.player();
            if (player.admin) {
                return;
            }

            int requiredMinutes = switch (playTimeLimit) {
                case GLOBAL_CHAT -> secretsConfig.chat.global.minPlayTimeMinutes;
                case VOTE_KICK -> secretsConfig.moderation.votekick.minPlayTimeMinutes;
                case CUSTOM -> 0;
            };

            var session = sessionService.get().get(player.uuid());
            var data = session != null ? session.data : null;
            if (data != null && data.totalPlayTime < requiredMinutes) {
                throw new XCoreCommandException("error-playtime-requirement", args("time", requiredMinutes));
            }
        });
    }
}
