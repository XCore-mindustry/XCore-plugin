package org.xcore.plugin.service.moderation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModerationResultTest {

    @Test
    @DisplayName("success with message and data keeps both values")
    void successWithMessageAndData() {
        var result = ModerationResult.success("ok", 42);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("ok");
        assertThat(result.getData()).contains(42);
    }

    @Test
    @DisplayName("success with data only keeps empty message")
    void successWithDataOnly() {
        var result = ModerationResult.success("payload");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEmpty();
        assertThat(result.getData()).contains("payload");
    }

    @Test
    @DisplayName("failure keeps message and empty data")
    void failureKeepsMessage() {
        var result = ModerationResult.<String>failure("error");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("error");
        assertThat(result.getData()).isEmpty();
    }
}
