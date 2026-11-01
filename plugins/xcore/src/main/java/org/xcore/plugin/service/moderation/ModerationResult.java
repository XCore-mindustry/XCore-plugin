package org.xcore.plugin.service.moderation;

import java.util.Optional;

/**
 * Result of a moderation operation.
 * @param <T> The type of data returned (PlayerData, BanData, MuteData, etc.)
 */
public class ModerationResult<T> {
    private final boolean success;
    private final String message;
    private final T data;

    public ModerationResult(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public static <T> ModerationResult<T> success(String message, T data) {
        return new ModerationResult<>(true, message, data);
    }

    public static <T> ModerationResult<T> success(T data) {
        return success(null, data);
    }

    public static <T> ModerationResult<T> failure(String message) {
        return new ModerationResult<>(false, message, null);
    }

    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }
}
