import com.ospx.flubundle.compiler.CompilationResult;
import com.ospx.flubundle.compiler.Diagnostic;
import com.ospx.flubundle.compiler.FtlCompilationException;
import com.ospx.flubundle.compiler.FtlCompiler;
import com.ospx.flubundle.compiler.FunctionCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FtlCompilerTest {

    @TempDir
    Path tempDir;

    private Path createFtl(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file;
    }

    @Test
    void testValidFtlCompilesCleanly() throws IOException {
        createFtl("bundle_en.ftl", """
                hello = Hello, { $name }!
                ban-msg = Player { $nickname } banned for { DURATION($duration, style: "full", colored: "true", maxUnits: 2) }
                color-msg = { COLOR($title, color: "accent") }
                clean-name = { STRIP($rawName) }
                date-msg = Expired at { DATETIME($expireDate, dateStyle: "medium", timeStyle: "short") }
                """);

        CompilationResult result = FtlCompiler.compile(tempDir);
        assertFalse(result.hasErrors(), result.formatReport());
        assertEquals(0, result.errors().size());
        assertEquals(5, result.messagesCount());
    }

    @Test
    void testUnknownFunctionWithTypoSuggestion() throws IOException {
        createFtl("broken.ftl", """
                msg1 = Time: { DURATON($sec) }
                msg2 = Text: { COLR($val) }
                """);

        CompilationResult result = FtlCompiler.compile(tempDir);
        assertTrue(result.hasErrors());
        List<Diagnostic> errors = result.errors();
        assertEquals(2, errors.size());

        assertTrue(errors.get(0).message().contains("Unknown function 'DURATON()'"));
        assertTrue(errors.get(0).message().contains("Did you mean 'DURATION'?"));

        assertTrue(errors.get(1).message().contains("Unknown function 'COLR()'"));
        assertTrue(errors.get(1).message().contains("Did you mean 'COLOR'?"));
    }

    @Test
    void testWrongPositionalArgumentCount() throws IOException {
        createFtl("invalid_args.ftl", """
                no-args = { DURATION() }
                too-many-args = { STRIP($a, $b) }
                """);

        CompilationResult result = FtlCompiler.compile(tempDir);
        assertTrue(result.hasErrors());
        List<Diagnostic> errors = result.errors();
        assertEquals(2, errors.size());

        assertTrue(errors.get(0).message().contains("DURATION() expects 1 positional argument(s), but got 0"));
        assertTrue(errors.get(1).message().contains("STRIP() expects 1 positional argument(s), but got 2"));
    }

    @Test
    void testUnknownOptionNameWithTypoSuggestion() throws IOException {
        createFtl("bad_options.ftl", """
                test = { DURATION($time, styl: "compact") }
                """);

        CompilationResult result = FtlCompiler.compile(tempDir);
        assertTrue(result.hasErrors());
        Diagnostic err = result.errors().get(0);
        assertTrue(err.message().contains("Unknown option 'styl' in call to DURATION()"));
        assertTrue(err.message().contains("Did you mean 'style'?"));
    }

    @Test
    void testInvalidOptionEnumValue() throws IOException {
        createFtl("bad_enum.ftl", """
                test = { DURATION($time, style: "banana") }
                """);

        CompilationResult result = FtlCompiler.compile(tempDir);
        assertTrue(result.hasErrors());
        Diagnostic err = result.errors().get(0);
        assertTrue(err.message().contains("Invalid value 'banana' for option 'style' in DURATION()"));
        assertTrue(err.message().contains("Allowed values:"));
    }

    @Test
    void testInvalidColorOptionWithBrackets() throws IOException {
        createFtl("bad_color.ftl", """
                test = { COLOR($text, color: "[red]") }
                """);

        CompilationResult result = FtlCompiler.compile(tempDir);
        assertTrue(result.hasErrors());
        Diagnostic err = result.errors().get(0);
        assertTrue(err.message().contains("must not contain character '['"));
    }

    @Test
    void testSelectWithoutDefaultVariant() throws IOException {
        createFtl("select.ftl", """
                bad-sel = { $count ->
                    [one] Item
                    [other] Items
                }
                """);

        CompilationResult result = FtlCompiler.compile(tempDir);
        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(e -> e.message().contains("Expected one of the variants to be marked as default (*)")));
    }

    @Test
    void testSyntaxErrorFailsCompilation() throws IOException {
        createFtl("syntax.ftl", """
                valid = Works
                broken = { $incomplete
                """);

        CompilationResult result = FtlCompiler.compile(tempDir);
        assertTrue(result.hasErrors());
        assertThrows(FtlCompilationException.class, result::assertSuccess);
    }

    @Test
    void testCompileExistingResources() {
        Path existingResources = Path.of("src/test/resources/bundles");
        CompilationResult result = FtlCompiler.compile(existingResources);
        assertFalse(result.hasErrors(), result.formatReport());
        assertTrue(result.messagesCount() > 0);
    }
}
