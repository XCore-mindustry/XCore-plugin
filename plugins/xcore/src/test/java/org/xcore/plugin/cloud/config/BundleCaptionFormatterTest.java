package org.xcore.plugin.cloud.config;

import arc.files.Fi;
import com.ospx.flubundle.Bundle;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.caption.CaptionVariable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xcore.plugin.cloud.XCoreSender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BundleCaptionFormatterTest {

    private static final Caption AMBIGUOUS = Caption.of("argument.parse.failure.player_info.ambiguous");

    @TempDir
    Path bundles;

    private BundleCaptionFormatter formatter;
    private XCoreSender english;
    private XCoreSender russian;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(bundles.resolve("bundle_en.ftl"), """
                argument-parse-failure-player-info-ambiguous = '{ $input }' matches { $count ->
                    [one] one record
                   *[other] { $count } records
                }.
                """);
        Files.writeString(bundles.resolve("bundle_ru.ftl"), """
                argument-parse-failure-player-info-ambiguous = '{ $input }': { $count ->
                    [one] { $count } запись
                    [few] { $count } записи
                   *[many] { $count } записей
                }.
                """);
        Bundle bundle = new Bundle(Locale.ENGLISH);
        bundle.addSource(new Fi(bundles.toFile()));
        formatter = new BundleCaptionFormatter(bundle);

        english = mock(XCoreSender.class);
        when(english.locale()).thenReturn(Locale.ENGLISH);
        russian = mock(XCoreSender.class);
        when(russian.locale()).thenReturn(Locale.of("ru"));
    }

    private String format(XCoreSender recipient, Caption caption, String defaultText, String input, String count) {
        return formatter.formatCaption(caption, recipient, defaultText,
                List.of(CaptionVariable.of("input", input), CaptionVariable.of("count", count)));
    }

    @Test
    @DisplayName("caption variables are real arguments, so plural selectors pick the right form")
    void pluralsWork() {
        assertThat(format(english, AMBIGUOUS, "", "bob", "1")).isEqualTo("'bob' matches one record.");
        assertThat(format(english, AMBIGUOUS, "", "bob", "3")).isEqualTo("'bob' matches 3 records.");
        assertThat(format(russian, AMBIGUOUS, "", "bob", "3")).isEqualTo("'bob': 3 записи.");
        assertThat(format(russian, AMBIGUOUS, "", "bob", "5")).isEqualTo("'bob': 5 записей.");
    }

    @Test
    @DisplayName("text the sender typed cannot recolor a translated message")
    void translatedInputIsEscaped() {
        assertThat(format(english, AMBIGUOUS, "", "[red]bob[]", "2"))
                .isEqualTo("'[[red]bob[[]' matches 2 records.");
    }

    @Test
    @DisplayName("an untranslated caption keeps its default text, with the input escaped")
    void untranslatedCaptionUsesDefaultText() {
        Caption team = Caption.of("argument.parse.failure.team");

        String message = formatter.formatCaption(team, english, "'[white]<input>[]' is not a valid team.",
                List.of(CaptionVariable.of("input", "[scarlet]x")));

        assertThat(message).isEqualTo("'[white][[scarlet]x[]' is not a valid team.");
    }

    @Test
    @DisplayName("a non-numeric value of a numeric variable is still rendered, as text")
    void nonNumericCountIsText() {
        assertThat(format(english, AMBIGUOUS, "", "bob", "many")).isEqualTo("'bob' matches many records.");
    }
}
