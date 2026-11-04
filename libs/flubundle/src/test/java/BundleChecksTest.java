import com.ospx.flubundle.compiler.BundleChecks;
import com.ospx.flubundle.compiler.CompilationResult;
import com.ospx.flubundle.compiler.Diagnostic;
import com.ospx.flubundle.compiler.FtlCompilationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleChecksTest {

    @TempDir
    Path dir;

    private void write(String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    @Test
    void validBundlesPass() throws IOException {
        write("bundle_en.ftl", "hello = Hello, { $name }!\nbye = Bye\n");
        write("bundle_ru.ftl", "hello = Привет, { $name }!\n");

        assertDoesNotThrow(() -> BundleChecks.of(dir).assertValid());
    }

    @Test
    void reportsErrorsOfEveryCheck() throws IOException {
        write("bundle_en.ftl", "hello = Hello, { $name }! { -missing }\n");
        write("bundle_ru.ftl", "hello = Привет, { $nmae }!\n");

        CompilationResult result = BundleChecks.of(dir).check();
        List<String> errors = result.errors().stream().map(Diagnostic::message).toList();

        assertEquals(2, errors.size(), result.formatReport());
        assertTrue(errors.stream().anyMatch(m -> m.contains("Unknown term '-missing'")), errors.toString());
        assertTrue(errors.stream().anyMatch(m -> m.contains("$nmae")), errors.toString());
        assertThrows(FtlCompilationException.class, () -> BundleChecks.of(dir).assertValid());
    }

    @Test
    void externalReferencesAreAllowed() throws IOException {
        write("bundle_en.ftl", "title = { -xcore }\n");

        assertDoesNotThrow(() -> BundleChecks.of(dir).externalReferences("-xcore").assertValid());
    }

    @Test
    void requiredKeysMustExistInEachLocale() throws IOException {
        write("bundle_en.ftl", "a = A\nb = B\n");
        write("bundle_uk_UA.ftl", "a = А\n");

        CompilationResult result = BundleChecks.of(dir)
                .requireKeys(List.of("en", "uk-UA"), List.of("a", "b"))
                .check();

        assertEquals(1, result.errors().size(), result.formatReport());
        assertTrue(result.errors().get(0).message().contains("'uk_UA' is missing required key(s): b"),
                result.formatReport());
    }
}
