package org.xcore.plugin.localization;

import com.ospx.flubundle.compiler.CompilationResult;
import com.ospx.flubundle.compiler.FtlCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class FtlBundleCompilationTest {

    private static final Path BUNDLES_DIR = Path.of("src/main/resources/bundles");
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
    @DisplayName("all FTL bundles compile without syntax errors, unknown functions, or invalid arguments")
    void allFtlBundlesCompileCleanly() {
        CompilationResult result = FtlCompiler.compile(BUNDLES_DIR);
        assertThat(result.hasErrors())
                .withFailMessage(result.formatReport())
                .isFalse();
    }

    @Test
    @DisplayName("primary locales cover all standard cloud caption keys")
    void primaryLocalesCoverAllStandardCloudCaptionKeys() {
        CompilationResult result = FtlCompiler.compile(BUNDLES_DIR);
        var primaryLocales = List.of("bundle_en.ftl", "bundle_ru.ftl", "bundle_uk_UA.ftl");
        var missing = new ArrayList<String>();

        for (var filename : primaryLocales) {
            Set<String> keys = result.messageKeysForFile(BUNDLES_DIR.resolve(filename));
            var missingKeys = new LinkedHashSet<>(PRIMARY_CLOUD_CAPTION_KEYS);
            missingKeys.removeAll(keys);
            if (!missingKeys.isEmpty()) {
                missing.add(filename + " -> " + missingKeys);
            }
        }

        assertThat(missing)
                .withFailMessage("Primary locale bundles are missing standard cloud caption keys:%n%s",
                        String.join(System.lineSeparator(), missing))
                .isEmpty();
    }
}
