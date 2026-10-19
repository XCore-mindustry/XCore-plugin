package org.xcore.plugin.cloud.config;

import arc.util.CommandHandler;
import com.ospx.flubundle.Bundle;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.xcore.cloud.mindustry.ConflictStrategy;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.metrics.MetricsService;
import org.xcore.plugin.permission.PermissionService;
import org.xcore.plugin.permission.RemoteConsoleScope;
import org.xcore.plugin.session.SessionService;

@Singleton
public class CloudManagerFactory {

    private final Bundle bundle;
    private final Provider<SessionService> sessionService;
    private final MetricsService metricsService;
    private final PermissionService permissions;
    private final RemoteConsoleScope remoteConsole;
    private final CloudCaptionConfigurer cloudCaptionConfigurer;

    @Inject
    public CloudManagerFactory(Bundle bundle,
                               Provider<SessionService> sessionService,
                               MetricsService metricsService,
                               PermissionService permissions,
                               RemoteConsoleScope remoteConsole,
                               CloudCaptionConfigurer cloudCaptionConfigurer) {
        this.bundle = bundle;
        this.sessionService = sessionService;
        this.metricsService = metricsService;
        this.permissions = permissions;
        this.remoteConsole = remoteConsole;
        this.cloudCaptionConfigurer = cloudCaptionConfigurer;
    }

    public MindustryCommandManager<XCoreSender> createManager(CommandHandler handler) {
        SenderMapper<MindustrySender, XCoreSender> mapper = SenderMapper.create(
                base -> new XCoreSender(base, bundle, sessionService, remoteConsole.consoleActor()),
                XCoreSender::getHandle
        );

        MindustryCommandManager<XCoreSender> manager = new MindustryCommandManager<>(
                handler,
                new CommandTelemetryCoordinator(ExecutionCoordinator.simpleCoordinator(), metricsService),
                mapper
        );

        manager.setConflictStrategy(ConflictStrategy.OVERRIDE);
        manager.setPermissionChecker(permissions::has);
        cloudCaptionConfigurer.configure(manager);

        return manager;
    }
}
