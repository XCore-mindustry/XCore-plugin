package org.xcore.plugin.ui;

import mindustry.gen.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;

class DefaultMindustryMenuGatewayTest {

    private final DefaultMindustryMenuGateway gateway = new DefaultMindustryMenuGateway();

    @Test
    @DisplayName("openUri tolerates null player")
    void openUri_toleratesNullPlayer() {
        assertThatNoException().isThrownBy(() -> gateway.openUri(null, "https://example.com"));
    }

    @Test
    @DisplayName("openUri tolerates null connection")
    void openUri_toleratesNullConnection() {
        Player player = Player.create();
        player.con = null;
        assertThatNoException().isThrownBy(() -> gateway.openUri(player, "https://example.com"));
    }

    @Test
    @DisplayName("copyToClipboard tolerates null player")
    void copyToClipboard_toleratesNullPlayer() {
        assertThatNoException().isThrownBy(() -> gateway.copyToClipboard(null, "code"));
    }

    @Test
    @DisplayName("copyToClipboard tolerates null connection")
    void copyToClipboard_toleratesNullConnection() {
        Player player = Player.create();
        player.con = null;
        assertThatNoException().isThrownBy(() -> gateway.copyToClipboard(player, "code"));
    }
}
