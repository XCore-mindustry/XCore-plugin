package org.xcore.plugin.localization;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

final class OpenAIRetryPolicy {
    private static final long INITIAL_RETRY_DELAY_MILLIS = 500L;
    private static final long MAX_RETRY_DELAY_MILLIS = 8_000L;

    boolean isRetryableStatus(int statusCode) {
        return switch (statusCode) {
            case 408, 409, 429, 500, 502, 503, 504 -> true;
            default -> false;
        };
    }

    void backoff(int attempt, HttpResponse<String> response) throws InterruptedException {
        Thread.sleep(retryDelayMillis(attempt, response));
    }

    long retryDelayMillis(int attempt, HttpResponse<String> response) {
        long serverDelayMillis = parseRetryAfterMillis(response).orElse(0L);
        if (serverDelayMillis > 0L) {
            return Math.min(serverDelayMillis, MAX_RETRY_DELAY_MILLIS);
        }

        long exponentialDelayMillis = INITIAL_RETRY_DELAY_MILLIS * (1L << Math.max(0, attempt - 1));
        return Math.min(exponentialDelayMillis, MAX_RETRY_DELAY_MILLIS);
    }

    Optional<Long> parseRetryAfterMillis(HttpResponse<String> response) {
        if (response == null) {
            return Optional.empty();
        }

        Optional<String> retryAfterHeader = response.headers().firstValue("Retry-After")
                .or(() -> response.headers().firstValue("retry-after"));
        if (retryAfterHeader.isEmpty()) {
            return Optional.empty();
        }

        String retryAfter = retryAfterHeader.get().trim();
        if (retryAfter.isEmpty()) {
            return Optional.empty();
        }

        try {
            long seconds = Long.parseLong(retryAfter);
            if (seconds <= 0L) {
                return Optional.empty();
            }
            return Optional.of(TimeUnit.SECONDS.toMillis(seconds));
        } catch (NumberFormatException ignored) {
            // Try HTTP date format next.
        }

        try {
            long delayMillis = Duration.between(
                    Instant.now(),
                    ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
            ).toMillis();
            return delayMillis > 0L ? Optional.of(delayMillis) : Optional.empty();
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }
}
