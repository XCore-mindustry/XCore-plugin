package tilelogger;

import com.ospx.flubundle.compiler.CompilationResult;
import com.ospx.flubundle.compiler.FtlCompiler;
import com.ospx.flubundle.compiler.LocaleConsistencyChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FtlBundleCompilationTest {

    @Test
    @DisplayName("all FTL bundles compile without syntax errors, unknown functions, or invalid arguments")
    void allFtlBundlesCompileCleanly() {
        CompilationResult result = FtlCompiler.compile(Path.of("src/main/resources/bundles"));
        assertFalse(result.hasErrors(), result.formatReport());
    }

    @Test
    @DisplayName("translations only use variables the English message defines")
    void translationsUseEnglishVariables() {
        CompilationResult result = LocaleConsistencyChecker.check(Path.of("src/main/resources/bundles"), "en");
        assertFalse(result.hasErrors(), result.formatReport());
    }
}
