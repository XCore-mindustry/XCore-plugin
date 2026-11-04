package org.xcore.plugin.localization;

import com.ospx.flubundle.compiler.BundleChecks;
import org.incendo.cloud.caption.Caption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.parser.MindustryCaptionKeys;
import org.xcore.cloud.mindustry.selector.caption.SelectorCaptionKeys;
import org.xcore.plugin.cloud.config.CloudCaptionConfigurer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FtlBundleCompilationTest {

    private static final Path BUNDLES_DIR = Path.of("src/main/resources/bundles");
    private static final List<String> PRIMARY_LOCALES = List.of("en", "ru", "uk_UA");
    private static final Set<String> PRIMARY_CLOUD_CAPTION_KEYS = Set.of(
            "argument-parse-failure-boolean",
            "argument-parse-failure-number",
            "argument-parse-failure-char",
            "argument-parse-failure-string",
            "argument-parse-failure-uuid",
            "argument-parse-failure-enum",
            "argument-parse-failure-regex",
            "argument-parse-failure-flag-unknown",
            "argument-parse-failure-flag-duplicate-flag",
            "argument-parse-failure-flag-no-flag-started",
            "argument-parse-failure-flag-missing-argument",
            "argument-parse-failure-flag-no-permission",
            "argument-parse-failure-color",
            "argument-parse-failure-duration",
            "argument-parse-failure-aggregate-missing",
            "argument-parse-failure-aggregate-failure",
            "argument-parse-failure-either",
            "exception-unexpected",
            "exception-invalid-argument",
            "exception-no-such-command",
            "exception-no-permission",
            "exception-invalid-sender",
            "exception-invalid-sender-list",
            "exception-invalid-syntax"
    );

    @Test
    @DisplayName("bundles compile, resolve their references, use the English variables, and the primary "
            + "locales translate every Cloud and cloud-mindustry caption")
    void bundlesPassAllChecks() throws IllegalAccessException {
        BundleChecks.of(BUNDLES_DIR)
                .requireKeys(PRIMARY_LOCALES, PRIMARY_CLOUD_CAPTION_KEYS)
                .requireKeys(PRIMARY_LOCALES, cloudMindustryCaptionKeys())
                .assertValid();
    }

    /** XCore renders its own help, so the MindustryHelp captions are not needed. */
    private static Set<String> cloudMindustryCaptionKeys() throws IllegalAccessException {
        var keys = new LinkedHashSet<String>();
        for (Class<?> holder : List.of(MindustryCaptionKeys.class, SelectorCaptionKeys.class)) {
            for (Field field : holder.getFields()) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == Caption.class) {
                    Caption caption = (Caption) field.get(null);
                    if (!caption.key().startsWith("mindustry.help.")) {
                        keys.add(CloudCaptionConfigurer.bundleKey(caption));
                    }
                }
            }
        }
        assertThat(keys).isNotEmpty();
        return keys;
    }
}
