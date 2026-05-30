package org.xcore.plugin.command.controller.server;

import com.ospx.flubundle.Bundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.PlayerDisplayService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerBadgeInventoryChangedCommandV1;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgeAdminControllerTest {

    @Test
    @DisplayName("badge grant publishes inventory change with structured server name")
    void grant_publishesInventoryChangeWithStructuredServerName() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        PlayerDataRepository playerDataRepository = mock(PlayerDataRepository.class);
        Session session = session("uuid-1", "Tester");
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.getAllCachedSnapshot()).thenReturn(List.of(session));
        when(sessionService.get("uuid-1")).thenReturn(session);

        NetworkService network = mock(NetworkService.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        BadgeAdminController controller = new BadgeAdminController(
                sessionService,
                network,
                playerDisplayService,
                playerDataRepository,
                config
        );

        controller.grant(mock(XCoreSender.class), "uuid-1", "translator");

        ArgumentCaptor<PlayerBadgeInventoryChangedCommandV1> eventCaptor = ArgumentCaptor.forClass(PlayerBadgeInventoryChangedCommandV1.class);
        verify(network).post(eventCaptor.capture());
        verify(playerDisplayService).refresh(session);
        verify(playerDataRepository).replaceUnlockedBadges("uuid-1", java.util.Set.of("translator"));
        verify(playerDataRepository).setActiveBadge("uuid-1", "");

        assertThat(eventCaptor.getValue().playerUuid()).isEqualTo("uuid-1");
        assertThat(eventCaptor.getValue().activeBadge()).isEmpty();
        assertThat(eventCaptor.getValue().unlockedBadges()).containsExactly("translator");
        assertThat(eventCaptor.getValue().server()).isEqualTo("mini-pvp");
    }

    private Session session(String uuid, String nickname) {
        mindustry.gen.Player player = mock(mindustry.gen.Player.class);
        when(player.uuid()).thenReturn(uuid);

        PlayerData data = new PlayerData();
        data.uuid = uuid;
        data.nickname = nickname;
        data.unlockedBadges = new HashSet<>();
        data.activeBadge = "";

        return new Session(
                new TomlSecretsConfig(),
                mock(Bundle.class),
                mock(MenuService.class),
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }
}
