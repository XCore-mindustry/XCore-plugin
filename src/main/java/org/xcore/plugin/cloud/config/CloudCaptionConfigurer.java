package org.xcore.plugin.cloud.config;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class CloudCaptionConfigurer {

    private final BundleService bundleService;
    private final Provider<SessionService> sessionService;

    @Inject
    public CloudCaptionConfigurer(BundleService bundleService,
                                  Provider<SessionService> sessionService) {
        this.bundleService = bundleService;
        this.sessionService = sessionService;
    }

    public void configure(MindustryCommandManager<XCoreSender> manager) {
        manager.captionRegistry().registerProvider((caption, recipient) -> {
            String key = caption.key().replace(".", "-");
            if (recipient.isPlayer()) {
                var session = sessionService.get().get(recipient.player());
                if (session != null) {
                    return session.locale().format(key, args());
                }
            }
            return bundleService.format(bundleService.getDefaultLocale(), key, args());
        });
    }
}
