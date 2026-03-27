package org.xcore.plugin.localization;

public sealed interface TranslationResult permits TranslationResult.Success, TranslationResult.Failure {

    boolean success();

    default boolean failed() {
        return !success();
    }

    static TranslationResult success(String translatedText) {
        return new Success(translatedText);
    }

    static TranslationResult failure(TranslationFailure failure) {
        return new Failure(failure);
    }

    record Success(String translatedText) implements TranslationResult {

        @Override
        public boolean success() {
            return true;
        }
    }

    record Failure(TranslationFailure failure) implements TranslationResult {

        @Override
        public boolean success() {
            return false;
        }
    }
}
