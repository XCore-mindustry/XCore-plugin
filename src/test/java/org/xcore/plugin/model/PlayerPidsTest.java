package org.xcore.plugin.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerPidsTest {

    @Test
    @DisplayName("negative and zero pids are real pids; only the sentinel is unassigned")
    void negativeAndZeroPidsAreAssigned() {
        assertThat(PlayerPids.isAssigned(-1)).isTrue();
        assertThat(PlayerPids.isAssigned(0)).isTrue();
        assertThat(PlayerPids.isAssigned(42)).isTrue();
        assertThat(PlayerPids.isAssigned(PlayerPids.NONE)).isFalse();
        assertThat(PlayerPids.isAssigned((Integer) null)).isFalse();
    }

    @Test
    @DisplayName("new players start without a pid so the repository assigns one")
    void newPlayersStartUnassigned() {
        assertThat(new PlayerData().pid).isEqualTo(PlayerPids.NONE);
        assertThat(new PlayerData("uuid", true).pid).isEqualTo(PlayerPids.NONE);
        assertThat(PlayerData.builder().build().pid).isEqualTo(PlayerPids.NONE);
    }

    @Test
    @DisplayName("orNull keeps negative pids and drops the sentinel")
    void orNullKeepsNegativePids() {
        assertThat(PlayerPids.orNull(-3)).isEqualTo(-3);
        assertThat(PlayerPids.orNull(PlayerPids.NONE)).isNull();
        assertThat(PlayerPids.orNull(null)).isNull();
        assertThat(PlayerPids.of(null)).isEqualTo(PlayerPids.NONE);
    }

    @Test
    @DisplayName("parse accepts plain, hash-prefixed and negative pids")
    void parseAcceptsNegativePids() {
        assertThat(PlayerPids.parse("12")).isEqualTo(12);
        assertThat(PlayerPids.parse("#12")).isEqualTo(12);
        assertThat(PlayerPids.parse("-12")).isEqualTo(-12);
        assertThat(PlayerPids.parse(" #-12 ")).isEqualTo(-12);
        assertThat(PlayerPids.parse("#0")).isEqualTo(0);
    }

    @Test
    @DisplayName("parse rejects names, overflow and the sentinel")
    void parseRejectsNonPids() {
        assertThat(PlayerPids.parse(null)).isNull();
        assertThat(PlayerPids.parse("")).isNull();
        assertThat(PlayerPids.parse("#")).isNull();
        assertThat(PlayerPids.parse("-")).isNull();
        assertThat(PlayerPids.parse("player-1")).isNull();
        assertThat(PlayerPids.parse("--1")).isNull();
        assertThat(PlayerPids.parse("99999999999")).isNull();
        assertThat(PlayerPids.parse(String.valueOf(Integer.MIN_VALUE))).isNull();
    }
}
