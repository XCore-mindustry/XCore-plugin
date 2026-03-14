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
import org.xcore.plugin.session.SessionService;

@Singleton
public class CloudManagerFactory {

    private final Bundle bundle;
    private final Provider<SessionService> sessionService;
    private final CloudPermissionPolicy cloudPermissionPolicy;
    private final CloudCaptionConfigurer cloudCaptionConfigurer;

    @Inject
    public CloudManagerFactory(Bundle bundle,
                               Provider<SessionService> sessionService,
                               CloudPermissionPolicy cloudPermissionPolicy,
                               CloudCaptionConfigurer cloudCaptionConfigurer) {
        this.bundle = bundle;
        this.sessionService = sessionService;
        this.cloudPermissionPolicy = cloudPermissionPolicy;
        this.cloudCaptionConfigurer = cloudCaptionConfigurer;
    }

    public MindustryCommandManager<XCoreSender> createManager(CommandHandler handler) {
        SenderMapper<MindustrySender, XCoreSender> mapper = SenderMapper.create(
                base -> new XCoreSender(base, bundle, sessionService),
                XCoreSender::getHandle
        );

        MindustryCommandManager<XCoreSender> manager = new MindustryCommandManager<>(
                handler,
                ExecutionCoordinator.simpleCoordinator(),
                mapper
        );

        manager.setConflictStrategy(ConflictStrategy.OVERRIDE);
        manager.setPermissionChecker(cloudPermissionPolicy::hasPermission);
        cloudCaptionConfigurer.configure(manager);

        return manager;
    }
}
