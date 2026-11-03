package org.xcore.plugin.service;

import com.ospx.flubundle.Bundle;
import mindustry.gen.Player;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.ui.MenuService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlayerActivityIdleTest {
    private final Player player = Player.create();
    private final Session session = new Session(new TomlSecretsConfig(), mock(Bundle.class),
            mock(MenuService.class), mock(PlayerDataRepository.class), player, new PlayerData());

    private int creditedMinutes(int ticks) {
        int credited = 0;
        for (int i = 0; i < ticks; i++) {
            if (!PlayerActivityService.idle(session, player)) credited++;
        }
        return credited;
    }

    @Test
    void aPlayerWhoDoesNothingStopsEarningAfterTheAfkLimit() {
        assertThat(creditedMinutes(30)).isEqualTo(PlayerActivityService.AFK_AFTER_MINUTES);
    }

    @Test
    void movingAimingOrChattingResetsTheIdleCount() {
        creditedMinutes(30);

        player.x += 80;
        assertThat(PlayerActivityService.idle(session, player)).as("moved").isFalse();
        creditedMinutes(PlayerActivityService.AFK_AFTER_MINUTES);

        player.mouseY += 80;
        assertThat(PlayerActivityService.idle(session, player)).as("aimed").isFalse();
        creditedMinutes(PlayerActivityService.AFK_AFTER_MINUTES);

        session.activeSinceTick = true;
        assertThat(PlayerActivityService.idle(session, player)).as("chatted or built").isFalse();
    }
}
