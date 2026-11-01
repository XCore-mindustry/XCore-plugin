package org.xcore.plugin.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardCursorTest {

    @Test
    @DisplayName("cursor is valid for non-sentinel pids including -1 and 0")
    void cursorIsValidForRegularPids() {
        assertThat(new LeaderboardCursor(100, 0, -1).isValid()).isTrue();
        assertThat(new LeaderboardCursor(100, 0, 0).isValid()).isTrue();
        assertThat(new LeaderboardCursor(100, 0, 42).isValid()).isTrue();
    }

    @Test
    @DisplayName("cursor is invalid only for sentinel marker pid")
    void cursorIsInvalidForSentinelPid() {
        assertThat(new LeaderboardCursor(0, 0, Integer.MIN_VALUE).isValid()).isFalse();
    }
}
