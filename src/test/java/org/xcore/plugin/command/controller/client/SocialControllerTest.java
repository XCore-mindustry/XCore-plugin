package org.xcore.plugin.command.controller.client;

import com.ospx.flubundle.Bundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.localization.TranslatorLanguagesProvider;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.ChatFormatService;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.TranslatorService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.MenuService;
import org.xcore.plugin.ui.menu.DiscordMenu;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatGlobalV1;
import org.xcore.protocol.generated.messages.chat.ChatMessages.ChatMessageV1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialControllerTest {

    @Test
    @DisplayName("global chat publishes server-scoped events using structured server name")
    void globalChat_publishesServerScopedEventsUsingStructuredServerName() {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = "mini-pvp";

        Session session = session("uuid-1", "[cyan]Tester", "Tester");
        SessionService sessionService = mock(SessionService.class);
        when(sessionService.get("uuid-1")).thenReturn(session);

        NetworkService network = mock(NetworkService.class);
        SocialController controller = new SocialController(
                sessionService,
                network,
                config,
                new TomlSecretsConfig(),
                mock(TranslatorLanguagesProvider.class),
                mock(ChatFormatService.class),
                mock(TranslatorService.class),
                mock(DiscordLinkService.class),
                mock(DiscordMenu.class)
        );

        XCoreSender sender = mock(XCoreSender.class);
        when(sender.player()).thenReturn(session.player);

        controller.globalChat(sender, "he`llo");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(network, org.mockito.Mockito.times(2)).post(eventCaptor.capture());

        assertThat(eventCaptor.getAllValues())
                .containsExactly(
                        new ChatGlobalV1("[cyan]Tester", "he`llo", "mini-pvp"),
                        new ChatMessageV1("Tester", "[mini-pvp] he*llo", "global")
                );
    }

    private Session session(String uuid, String coloredName, String plainName) {
        mindustry.gen.Player player = mock(mindustry.gen.Player.class);
        when(player.uuid()).thenReturn(uuid);
        when(player.coloredName()).thenReturn(coloredName);
        when(player.plainName()).thenReturn(plainName);

        PlayerData data = new PlayerData();
        data.uuid = uuid;

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
