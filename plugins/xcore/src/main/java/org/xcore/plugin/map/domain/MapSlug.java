package org.xcore.plugin.map.domain;

import arc.util.Strings;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Non-unique display alias for humans, bots, and logs: sanitized "author/name".
 * Never use this value as a persistent map ID. Arc markup stripping requires
 * the registered engine palette. The factory treats author "unknown" as missing.
 */
public record MapSlug(String author, String name) {
    private static final String DEFAULT_AUTHOR = "community";
    private static final String DEFAULT_NAME = "unnamed";

    public MapSlug {
        if (author == null || author.isBlank() || !author.equals(slugify(author))) {
            throw new IllegalArgumentException("Slug author must be canonical kebab-case: " + author);
        }
        if (name == null || name.isBlank() || !name.equals(slugify(name))) {
            throw new IllegalArgumentException("Slug name must be canonical kebab-case: " + name);
        }
    }

    public static MapSlug of(String rawAuthor, String rawName) {
        String sanitizedAuthor = sanitize(rawAuthor);
        String sanitizedName = sanitize(rawName);
        return new MapSlug(
                sanitizedAuthor == null || sanitizedAuthor.isBlank() || sanitizedAuthor.equals("unknown")
                        ? DEFAULT_AUTHOR : sanitizedAuthor,
                sanitizedName == null || sanitizedName.isBlank() ? DEFAULT_NAME : sanitizedName
        );
    }

    public String asSlug() {
        return author + "/" + name;
    }

    /**
     * Strips Arc markup and engine glyphs first (so color tags do not glue words
     * together), then normalizes and kebab-cases without removing semantic marks.
     */
    private static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String stripped = Strings.stripColors(Strings.stripGlyphs(input));
        return slugify(stripped);
    }

    private static String slugify(String input) {
        if (input == null) {
            return null;
        }
        String normalized = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        String kebab = normalized
                .replaceAll("[^\\p{L}\\p{Nd}\\p{M}]+", "-")
                .replaceAll("(^|-)\\p{M}+", "$1")
                .replaceAll("^-+|-+$", "");
        return kebab.isBlank() ? null : kebab;
    }
}
