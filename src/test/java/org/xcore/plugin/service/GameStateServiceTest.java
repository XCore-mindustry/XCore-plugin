package org.xcore.plugin.service;

import arc.Core;
import arc.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameStateServiceTest {

    private Settings previousSettings;

    @BeforeEach
    void setUp() {
        previousSettings = Core.settings;
        Core.settings = mock(Settings.class);
    }

    @AfterEach
    void tearDown() {
        Core.settings = previousSettings;
    }

    @Test
    @DisplayName("reloadWorld with null action is no-op and does not throw")
    void reloadWorld_withNullAction_doesNotThrow() {
        GameStateService service = new GameStateService();

        assertThatCode(() -> service.reloadWorld(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("loadMap with null map logs error and does not throw NPE")
    void loadMap_withNullMap_doesNotThrow() {
        GameStateService service = new GameStateService();

        assertThatCode(() -> service.loadMap(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("loadMap with invalid gamemode setting falls back to survival safely")
    void loadMap_withInvalidGamemode_fallsBackSafely() {
        when(Core.settings.getString("lastServerMode", "survival")).thenReturn("INVALID_GAMEMODE_XYZ");
        GameStateService service = new GameStateService();

        // Should not throw IllegalArgumentException on unknown gamemode string
        assertThatCode(() -> service.loadMap(null)).doesNotThrowAnyException();
    }
}
