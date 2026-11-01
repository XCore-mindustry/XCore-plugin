package org.xcore.plugin.localization;

public record TranslationFailure(String providerName, String reason, Throwable cause) {

    public static TranslationFailure unavailable(String providerName, String reason) {
        return new TranslationFailure(providerName, reason, null);
    }

    public static TranslationFailure failed(String providerName, Throwable cause) {
        String reason = cause == null ? "unknown" : cause.getMessage();
        return new TranslationFailure(providerName, reason, cause);
    }
}
