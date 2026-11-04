package tilelogger;

import com.ospx.flubundle.compiler.BundleChecks;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class FtlBundleCompilationTest {

    @Test
    @DisplayName("bundles compile, resolve their references and use the English variables")
    void bundlesPassAllChecks() {
        BundleChecks.of(Path.of("src/main/resources/bundles")).assertValid();
    }
}
