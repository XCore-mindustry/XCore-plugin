package org.xcore.plugin.service;

import mindustry.Vars;
import mindustry.core.GameState;
import mindustry.game.Gamemode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.xcore.plugin.config.TomlXcoreConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoHostServiceTest {

    @ParameterizedTest
    @CsvSource({
            "survival, survival",
            "SURVIVAL, survival",
            "'  survival  ', survival",
            "pvp, pvp",
            "PVP, pvp",
            "'  pvp  ', pvp",
            "attack, attack",
            "Attack, attack",
            "sandbox, sandbox",
            "SANDBOX, sandbox"
    })
    @DisplayName("parseGamemode parses valid standard modes regardless of case or whitespace")
    void parseGamemode_parsesValidModes(String input, String expectedModeName) {
        Gamemode mode = AutoHostService.parseGamemode(input);
        assertThat(mode).isEqualTo(Gamemode.valueOf(expectedModeName));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "editor", "EDITOR", "unknown", "invalid_mode", "nonexistent"})
    @DisplayName("parseGamemode falls back to survival on blank, hidden, or invalid mode")
    void parseGamemode_fallsBackToSurvival(String input) {
        Gamemode mode = AutoHostService.parseGamemode(input);
        assertThat(mode).isEqualTo(Gamemode.survival);
    }

    @Test
    @DisplayName("parseGamemode falls back to survival on null input")
    void parseGamemode_nullInput_returnsSurvival() {
        Gamemode mode = AutoHostService.parseGamemode(null);
        assertThat(mode).isEqualTo(Gamemode.survival);
    }

    @Test
    @DisplayName("onServerLoad returns false when autoStart is disabled")
    void onServerLoad_disabled_returnsFalse() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.autoStart = false;

        AutoHostService service = new AutoHostService(config);

        boolean hosted = service.onServerLoad();
        assertThat(hosted).isFalse();
    }

    @Test
    @DisplayName("onServerLoad returns false when server is already in game")
    void onServerLoad_alreadyHosting_returnsFalse() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.autoStart = true;

        AutoHostService service = new AutoHostService(config);

        GameState oldState = Vars.state;
        try {
            Vars.state = new GameState();
            Vars.state.set(GameState.State.playing);

            boolean hosted = service.onServerLoad();
            assertThat(hosted).isFalse();
        } finally {
            Vars.state = oldState;
        }
    }

    @Test
    @DisplayName("onServerLoad returns false when no map is available")
    void onServerLoad_noMapAvailable_returnsFalse() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.autoStart = true;

        AutoHostService service = new AutoHostService(config);

        GameState oldState = Vars.state;
        mindustry.maps.Maps oldMaps = Vars.maps;
        try {
            Vars.state = new GameState();
            Vars.maps = mock(mindustry.maps.Maps.class);
            when(Vars.maps.getNextMap(any(), any())).thenReturn(null);

            boolean hosted = service.onServerLoad();
            assertThat(hosted).isFalse();
        } finally {
            Vars.state = oldState;
            Vars.maps = oldMaps;
        }
    }

    @Test
    @DisplayName("initialize is idempotent and does not throw on repeated calls")
    void initialize_isIdempotent() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        AutoHostService service = new AutoHostService(config);

        assertThatCode(() -> {
            service.initialize();
            service.initialize();
        }).doesNotThrowAnyException();
    }
}
