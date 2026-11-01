package org.xcore.plugin.integration;

import com.google.gson.Gson;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.service.AdminAuthService;
import org.xcore.plugin.service.DiscordLinkService;
import org.xcore.plugin.service.moderation.ModerationService;
import org.xcore.plugin.ui.menu.DiscordMenu;
import org.xcore.plugin.session.SessionService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AdminModIntegrationTest {

    private NetServer previousNetServer;
    private Administration admins;
    private NetServer netServer;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        admins = mock(Administration.class);
        netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;
    }

    @AfterEach
    void tearDown() {
        Vars.netServer = previousNetServer;
    }

    @Test
    @DisplayName("holdVanillaBan delegates to vanilla admin ban")
    void holdVanillaBanDelegatesToVanillaAdminBan() {
        var integration = new AdminModIntegration(
                mock(PlayerDataRepository.class),
                mock(SessionService.class),
                mock(ModerationService.class),
                mock(AdminAuthService.class),
                mock(DiscordLinkService.class),
                mock(DiscordMenu.class),
                new Gson(),
                mock(org.xcore.plugin.concurrent.Async.class),
                new org.xcore.plugin.permission.PermissionService()
        );

        integration.holdVanillaBan("uuid-1");
        integration.holdVanillaBan("uuid-1");

        verify(admins, times(2)).banPlayerID("uuid-1");
    }

    @Test
    @DisplayName("Discord link request reads the code off the game thread")
    @SuppressWarnings("unchecked")
    void discordLinkRequestUsesAsyncCodeRequest() {
        var sessions = mock(SessionService.class);
        var links = mock(DiscordLinkService.class);
        var integration = new AdminModIntegration(mock(PlayerDataRepository.class), sessions, mock(ModerationService.class),
                mock(AdminAuthService.class), links, mock(DiscordMenu.class), new Gson(),
                mock(org.xcore.plugin.concurrent.Async.class), new org.xcore.plugin.permission.PermissionService());
        integration.init();
        var handler = org.mockito.ArgumentCaptor.forClass(arc.func.Cons2.class);
        verify(netServer).addPacketHandler(eq("adm_discord_link_req"), handler.capture());
        var player = mock(mindustry.gen.Player.class);
        player.con = mock(mindustry.net.NetConnection.class);
        var session = mock(org.xcore.plugin.session.Session.class);
        session.data = org.xcore.plugin.model.PlayerData.builder().uuid("uuid-1").adminModVersion("1").build();
        when(sessions.get(player)).thenReturn(session);

        handler.getValue().get(player, "");

        verify(links).requestCode(eq(session), eq(false), any());
        verify(links, never()).getOrCreateActiveCode(any(org.xcore.plugin.session.Session.class));
    }
}
