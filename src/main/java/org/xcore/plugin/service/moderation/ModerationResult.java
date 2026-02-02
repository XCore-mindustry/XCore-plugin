package org.xcore.plugin.service.moderation;

import lombok.Builder;
import lombok.Getter;

import java.util.Optional;

/**
 * Result of a moderation operation.
 * @param <T> The type of data returned (PlayerData, BanData, MuteData, etc.)
 */
@Getter
@Builder
public class ModerationResult<T> {
    private final boolean success;
    private final String message;
    private final T data;

    public static <T> ModerationResult<T> success(String message, T data) {
        return ModerationResult.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ModerationResult<T> success(T data) {
        return success(null, data);
    }

    public static <T> ModerationResult<T> failure(String message) {
        return ModerationResult.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();
    }

    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }

    public Optional<String> getMessage() {
        return Optional.ofNullable(message);
    }
}
