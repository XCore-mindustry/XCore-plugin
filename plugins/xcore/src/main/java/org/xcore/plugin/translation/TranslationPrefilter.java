package org.xcore.plugin.translation;

import arc.util.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides which chat text is worth a provider call and cleans it for one. Colour tags and
 * Mindustry icons are removed first: the translation is shown in its own colour anyway, and
 * tags handed to a provider come back mangled.
 */
@Singleton
public class TranslationPrefilter {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LINK = Pattern.compile("(?i)^(?:https?://|www\\.)\\S+$");

    private final TomlXcoreConfig config;

    @Inject
    public TranslationPrefilter(TomlXcoreConfig config) {
        this.config = config;
    }

    /** Returns the text to translate, or {@code null} when the message needs no translation. */
    public String prepare(String text) {
        if (text == null) {
            return null;
        }

        String cleaned = WHITESPACE.matcher(Strings.stripGlyphs(Strings.stripColors(text))).replaceAll(" ").strip();
        if (cleaned.isEmpty()) {
            return null;
        }

        var prefilter = config.translation.prefilter;
        if (!prefilter.enabled) {
            return cleaned;
        }

        if (LINK.matcher(cleaned).matches()) {
            return null;
        }

        String letters = lettersOnly(cleaned);
        if (letters.codePointCount(0, letters.length()) < prefilter.minLetters) {
            return null;
        }

        for (String skipWord : prefilter.skipWords) {
            if (skipWord != null && letters.equals(lettersOnly(skipWord))) {
                return null;
            }
        }

        return cleaned;
    }

    private static String lettersOnly(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        text.codePoints()
                .filter(Character::isLetter)
                .forEach(builder::appendCodePoint);
        return builder.toString().toLowerCase(Locale.ROOT);
    }
}
