package org.xcore.plugin.cloud.config;

import arc.util.CommandHandler;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.xcore.cloud.mindustry.ConflictStrategy;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.cloud.mindustry.MindustrySender;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.session.SessionService;

@Singleton
public class CloudManagerFactory {

    private final BundleService bundleService;
    private final Provider<SessionService> sessionService;
    private final CloudPermissionPolicy cloudPermissionPolicy;
    private final CloudCaptionConfigurer cloudCaptionConfigurer;

    @Inject
    public CloudManagerFactory(BundleService bundleService,
                               Provider<SessionService> sessionService,
                               CloudPermissionPolicy cloudPermissionPolicy,
                               CloudCaptionConfigurer cloudCaptionConfigurer) {
        this.bundleService = bundleService;
        this.sessionService = sessionService;
        this.cloudPermissionPolicy = cloudPermissionPolicy;
        this.cloudCaptionConfigurer = cloudCaptionConfigurer;
    }

    public MindustryCommandManager<XCoreSender> createManager(CommandHandler handler) {
        SenderMapper<MindustrySender, XCoreSender> mapper = SenderMapper.create(
                base -> new XCoreSender(base, bundleService, sessionService),
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
