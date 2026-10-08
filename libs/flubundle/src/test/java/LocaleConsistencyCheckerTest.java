import com.ospx.flubundle.compiler.CompilationResult;
import com.ospx.flubundle.compiler.Diagnostic;
import com.ospx.flubundle.compiler.LocaleConsistencyChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocaleConsistencyCheckerTest {

    @TempDir
    Path dir;

    private void write(String name, String content) throws IOException {
        Files.writeString(dir.resolve(name), content);
    }

    private static List<String> messages(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(d -> d.messageId() + ": " + d.message()).toList();
    }

    @Test
    void consistentLocalesPass() throws IOException {
        write("bundle_en.ftl", """
                apples = { $count ->
                    [one] { $count } apple
                   *[other] { $count } apples
                }
                """);
        write("bundle_ru.ftl", """
                apples = { $count ->
                    [one] { $count } яблоко
                    [few] { $count } яблока
                   *[other] { $count } яблок
                }
                """);

        CompilationResult result = LocaleConsistencyChecker.check(dir, "en");

        assertFalse(result.hasErrors(), result.formatReport());
        assertFalse(result.hasWarnings(), result.formatReport());
        assertEquals(2, result.filesCount());
    }

    @Test
    void reportsVariableMismatchesOrphansAndMissingKeys() throws IOException {
        write("bundle_en.ftl", """
                greet = Hello, { $name }!
                kick = { $player } was kicked: { $reason }
                untranslated = Only English
                """);
        write("bundle_uk_UA.ftl", """
                greet = Привіт, { $username }!
                kick = { $player } вигнаний
                extra = Зайвий ключ
                """);

        CompilationResult result = LocaleConsistencyChecker.check(dir, "en");

        assertEquals(1, result.errors().size(), result.formatReport());
        assertTrue(messages(result.errors()).getFirst().startsWith("greet: Uses $username"), result.formatReport());

        var warnings = messages(result.warnings());
        assertTrue(warnings.contains("null: 1 key(s) missing, falling back to 'en': untranslated"), result.formatReport());
        assertTrue(warnings.contains("greet: Does not use $name from base locale 'en'"), result.formatReport());
        assertTrue(warnings.contains("kick: Does not use $reason from base locale 'en'"), result.formatReport());
        assertTrue(warnings.contains("extra: Key does not exist in base locale 'en'"), result.formatReport());
    }

    @Test
    void followsMessageReferencesAndCanSkipMissingKeys() throws IOException {
        write("bundle_en.ftl", """
                author = by { $author }
                post = { $title } { author }
                """);
        write("bundle_de.ftl", """
                post = { $title } von { $author }
                """);

        CompilationResult result = LocaleConsistencyChecker.builder()
                .baseLocale("en")
                .reportMissingKeys(false)
                .build()
                .check(dir);

        assertFalse(result.hasErrors(), result.formatReport());
        assertFalse(result.hasWarnings(), result.formatReport());
    }

    @Test
    void failsWithoutBaseLocale() throws IOException {
        write("bundle_ru.ftl", "key = Значение\n");

        assertTrue(LocaleConsistencyChecker.check(dir, "en").hasErrors());
    }
}
