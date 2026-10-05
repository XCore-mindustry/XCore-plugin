package org.xcore.plugin.cloud.config;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.incendo.cloud.caption.Caption;
import org.xcore.plugin.XcorePlugin;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.localization.BundlePlaceholderRegistry;
import org.xcore.plugin.session.SessionService;

import java.util.Map;

@Singleton
public class CloudCaptionConfigurer {

    private final Bundle bundle;
    private final Provider<SessionService> sessionService;
    private final BundlePlaceholderRegistry placeholderRegistry;

    @Inject
    public CloudCaptionConfigurer(Bundle bundle,
                                  Provider<SessionService> sessionService) {
        this.bundle = bundle;
        this.sessionService = sessionService;
        this.placeholderRegistry = BundlePlaceholderRegistry.fromMod(XcorePlugin.class);
    }

    public void configure(MindustryCommandManager<XCoreSender> manager) {
        manager.captionRegistry().registerProvider((caption, recipient) -> {
            String key = bundleKey(caption);
            if (!placeholderRegistry.containsKey(key)) {
                return null;
            }

            Map<String, Object> args = placeholderRegistry.placeholderArgs(key);
            if (recipient.isPlayer()) {
                var session = sessionService.get().get(recipient.player());
                if (session != null) {
                    return session.locale().format(key, args);
                }
            }
            return bundle.format(recipient.locale(), key, args);
        });
    }

    /**
     * Maps a Cloud caption key to its bundle key: {@code exception.invalid_argument} becomes
     * {@code exception-invalid-argument}.
     */
    public static String bundleKey(Caption caption) {
        return caption.key().replace('.', '-').replace('_', '-');
    }
}
