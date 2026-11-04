package org.xcore.plugin.cloud.config;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.caption.Caption;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;

/**
 * Localizes Cloud's messages: every caption is rendered by {@link BundleCaptionFormatter}.
 */
@Singleton
public class CloudCaptionConfigurer {

    private final Bundle bundle;

    @Inject
    public CloudCaptionConfigurer(Bundle bundle) {
        this.bundle = bundle;
    }

    public void configure(MindustryCommandManager<XCoreSender> manager) {
        manager.captionFormatter(new BundleCaptionFormatter(bundle));
    }

    /**
     * Maps a Cloud caption key to its bundle key: {@code exception.invalid_argument} becomes
     * {@code exception-invalid-argument}.
     */
    public static String bundleKey(Caption caption) {
        return caption.key().replace('.', '-').replace('_', '-');
    }
}
