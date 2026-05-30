package org.xcore.plugin.command.controller.client;

import com.ospx.flubundle.Bundle;
import com.ospx.flubundle.BundleContext;
import com.ospx.flubundle.Localizer;
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
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.protocol.generated.messages.chat.ChatMessages.PlayerActiveBadgeChangedCommandV1;

import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BadgeControllerTest {

    @Test
    @DisplayName("badge clear publishes active badge change with structured server name")
    void clear_publishesActiveBadgeChangeWithStructuredServerName() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";
        Session session = session("uuid-1", "translator");
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.get("uuid-1")).thenReturn(session);
        doAnswer(invocation -> {
            Session target = invocation.getArgument(0);
            target.data.activeBadge = invocation.getArgument(1);
            return true;
        }).when(sessionService).setActiveBadge(any(Session.class), anyString());

        PlayerMenu playerMenu = mock(PlayerMenu.class);
        PlayerDisplayService playerDisplayService = mock(PlayerDisplayService.class);
        NetworkService network = mock(NetworkService.class);
        BadgeController controller = new BadgeController(config, sessionService, playerMenu, playerDisplayService, network);

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.player()).thenReturn(session.player);

        controller.clear(sender);

        ArgumentCaptor<PlayerActiveBadgeChangedCommandV1> eventCaptor = ArgumentCaptor.forClass(PlayerActiveBadgeChangedCommandV1.class);
        verify(network).post(eventCaptor.capture());
        verify(playerDisplayService).refresh(session);
        assertThat(eventCaptor.getValue().playerUuid()).isEqualTo("uuid-1");
        assertThat(eventCaptor.getValue().activeBadge()).isEmpty();
        assertThat(eventCaptor.getValue().server()).isEqualTo("mini-pvp");
    }

    private Session session(String uuid, String activeBadge) {
        mindustry.gen.Player player = player(uuid);
        Bundle bundle = mock(Bundle.class);
        Localizer localizer = mock(Localizer.class);
        BundleContext context = mock(BundleContext.class);
        when(bundle.localizer(any(Supplier.class))).thenReturn(localizer);
        when(bundle.context(any(mindustry.gen.Player.class), any(Supplier.class))).thenReturn(context);
        when(localizer.locale()).thenReturn(Locale.ENGLISH);
        when(localizer.format(anyString(), anyMap())).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerData data = new PlayerData();
        data.uuid = uuid;
        data.activeBadge = activeBadge;

        return new Session(
                new TomlSecretsConfig(),
                bundle,
                mock(MenuService.class),
                mock(PlayerDataRepository.class),
                player,
                data
        );
    }

    private mindustry.gen.Player player(String uuid) {
        mindustry.gen.Player player = mock(mindustry.gen.Player.class);
        when(player.uuid()).thenReturn(uuid);
        return player;
    }
}
