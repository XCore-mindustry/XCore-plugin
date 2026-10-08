package com.ospx.flubundle;

import java.util.Locale;

/**
 * Locale code helpers shared by {@link Bundle} and the build-time checks.
 */
public final class LocaleCodes {

    private LocaleCodes() {
    }

    /**
     * Normalizes codes like {@code en-US}, {@code EN_us} or {@code en} to {@code en_US} / {@code en}.
     *
     * @return the normalized code, or {@code null} if the code is blank
     */
    public static String normalize(String code) {
        if (code == null) {
            return null;
        }

        var normalized = code.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        normalized = normalized.replace('-', '_');
        var codes = normalized.split("_");
        if (codes.length == 0 || codes[0].isBlank()) {
            return null;
        }

        if (codes.length == 1 || codes[1].isBlank()) {
            return codes[0].toLowerCase(Locale.ROOT);
        }

        return codes[0].toLowerCase(Locale.ROOT) + "_" + codes[1].toUpperCase(Locale.ROOT);
    }

    /**
     * Normalizes a locale to its {@code language[_COUNTRY]} code.
     *
     * @return the normalized code, or {@code null} if the locale has no language
     */
    public static String normalize(Locale locale) {
        if (locale == null) {
            return null;
        }

        var language = locale.getLanguage();
        if (language == null || language.isBlank()) {
            return null;
        }

        var country = locale.getCountry();
        if (country == null || country.isBlank()) {
            return language.toLowerCase(Locale.ROOT);
        }

        return language.toLowerCase(Locale.ROOT) + "_" + country.toUpperCase(Locale.ROOT);
    }

    /**
     * Parses a locale code into a {@link Locale}.
     *
     * @return the locale, or {@code null} if the code is blank
     */
    public static Locale parse(String code) {
        var normalized = normalize(code);
        if (normalized == null) {
            return null;
        }

        var codes = normalized.split("_");
        return codes.length >= 2 ? Locale.of(codes[0], codes[1]) : Locale.of(codes[0]);
    }

    /**
     * Extracts the locale code from a bundle file name without extension:
     * {@code bundle_en} → {@code en}, {@code bundle_uk_UA} → {@code uk_UA}.
     *
     * @return the locale code, or {@code null} if the name has no locale suffix
     */
    public static String fromFileName(String nameWithoutExtension) {
        int lastUnderscore = nameWithoutExtension.lastIndexOf('_');
        if (lastUnderscore == -1) {
            return null;
        }

        int secondLastUnderscore = nameWithoutExtension.lastIndexOf('_', lastUnderscore - 1);
        if (secondLastUnderscore != -1 && lastUnderscore - secondLastUnderscore == 3) {
            return nameWithoutExtension.substring(secondLastUnderscore + 1);
        }

        return nameWithoutExtension.substring(lastUnderscore + 1);
    }
}
