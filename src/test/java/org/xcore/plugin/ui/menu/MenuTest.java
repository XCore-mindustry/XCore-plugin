package org.xcore.plugin.ui.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.localization.Localization;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuTest {

    @Test
    @DisplayName("formatPlayTime omits empty units and keeps localized order")
    void formatPlayTimeOmitsEmptyUnitsAndKeepsLocalizedOrder() {
        var menu = new Menu(null, null, null);
        var local = mock(Localization.class);

        when(local.t("player-menu-time-days", java.util.Map.of("value", 1))).thenReturn("1d");
        when(local.t("player-menu-time-hours", java.util.Map.of("value", 2))).thenReturn("2h");
        when(local.t("player-menu-time-minutes", java.util.Map.of("value", 5))).thenReturn("5m");

        assertThat(menu.formatPlayTime(24 * 60 + 2 * 60 + 5, local)).isEqualTo("1d 2h 5m");
    }

    @Test
    @DisplayName("formatPlayTime falls back to zero minutes")
    void formatPlayTimeFallsBackToZeroMinutes() {
        var menu = new Menu(null, null, null);
        var local = mock(Localization.class);

        when(local.t("player-menu-time-minutes", java.util.Map.of("value", 0))).thenReturn("0m");

        assertThat(menu.formatPlayTime(0, local)).isEqualTo("0m");
    }
}
