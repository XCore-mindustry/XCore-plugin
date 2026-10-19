package org.xcore.plugin.cloud.config;

import com.ospx.flubundle.Bundle;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.incendo.cloud.caption.Caption;
import org.xcore.cloud.mindustry.MindustryCommandManager;
import org.xcore.plugin.cloud.XCoreSender;

import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class CloudCaptionConfigurer {

    private final Bundle bundle;

    @Inject
    public CloudCaptionConfigurer(Bundle bundle) {
        this.bundle = bundle;
    }

    public void configure(MindustryCommandManager<XCoreSender> manager) {
        manager.captionRegistry().registerProvider((caption, recipient) -> {
            String key = bundleKey(caption);
            if (!bundle.has(key)) {
                return null;
            }

            return bundle.format(recipient.locale(), key, placeholderArgs(key));
        });
    }

    /**
     * Renders each {@code $variable} of the message as a {@code <variable>} placeholder, which Cloud
     * then fills with the caption variables.
     */
    private Map<String, Object> placeholderArgs(String key) {
        var args = new LinkedHashMap<String, Object>();
        for (String name : bundle.variables(key)) {
            args.put(name, "<" + name + ">");
        }
        return args;
    }

    /**
     * Maps a Cloud caption key to its bundle key: {@code exception.invalid_argument} becomes
     * {@code exception-invalid-argument}.
     */
    public static String bundleKey(Caption caption) {
        return caption.key().replace('.', '-').replace('_', '-');
    }
}
