package org.xcore.plugin.cases;

import org.junit.jupiter.api.Test;
import org.xcore.plugin.chat.ChatDeliveryService;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.client.SocialController;
import org.xcore.plugin.localization.TranslatorLanguagesProvider;
import org.xcore.plugin.ui.menu.DiscordMenu;
import org.xcore.plugin.event.net.chat.ChatMessageHandler;
import org.xcore.plugin.event.net.chat.VoteChatInterceptor;
import org.xcore.plugin.service.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChatEvidenceHooksTest {
    private final CasesWorld world = new CasesWorld();
    private final org.xcore.plugin.session.Session author = world.session("target", 7, "address");
    private final SecurityService security = mock(SecurityService.class);
    private final VoteChatInterceptor votes = mock(VoteChatInterceptor.class);
    private final ChatFormatService format = mock(ChatFormatService.class);
    private final ChatDeliveryService delivery = mock(ChatDeliveryService.class);
    private final ChatMessageHandler handler = new ChatMessageHandler(world.config, delivery, world.network,
            security, format, votes, world.chat);
    private final SocialController social = new SocialController(world.sessions, world.network, world.config,
            world.secrets, mock(TranslatorLanguagesProvider.class), delivery, world.chat,
            mock(DiscordLinkService.class), mock(DiscordMenu.class));

    private void teamChat(String text) {
        XCoreSender sender = mock(XCoreSender.class);
        when(sender.player()).thenReturn(author.player);
        social.teamChat(sender, text);
    }

    @Test
    void publicChatAndTeamChatAreCapturedOnceWithCorrectVisibility() {
        when(world.sessions.findByTeam(author.player.team())).thenReturn(List.of());
        handler.handle(author.player, "public");
        teamChat("team");
        var lines = world.chat.excerpt("target", null, 30, 600);
        assertThat(lines).extracting(line -> line.text()).containsExactly("public", "team");
        assertThat(lines).extracting(line -> line.team()).containsExactly(false, true);
    }

    @Test
    void mutedInterceptedPasswordAndSlashCommandMessagesDoNotReachEvidence() {
        when(security.checkAndNotifyMuted(author.player)).thenReturn(true);
        handler.handle(author.player, "muted");
        when(security.checkAndNotifyMuted(author.player)).thenReturn(false);
        when(votes.intercept(author.player, "voted")).thenReturn(true);
        handler.handle(author.player, "voted");
        handler.handle(author.player, "/login secret");
        handler.handle(author.player, "/example command");
        assertThat(world.chat.excerpt("target", null, 30, 600)).isEmpty();
    }

    @Test
    void featureOffAddsNoEvidenceToExistingChatPaths() {
        world.secrets.cases.reports.enabled = false;
        handler.handle(author.player, "public");
        teamChat("team");
        assertThat(world.chat.excerpt("target", null, 30, 600)).isEmpty();
    }
}
