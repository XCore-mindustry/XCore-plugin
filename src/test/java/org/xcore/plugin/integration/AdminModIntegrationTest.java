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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AdminModIntegrationTest {

    private NetServer previousNetServer;
    private Administration admins;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        admins = mock(Administration.class);
        var netServer = mock(NetServer.class);
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
                new Gson()
        );

        integration.holdVanillaBan("uuid-1");
        integration.holdVanillaBan("uuid-1");

        verify(admins, times(2)).banPlayerID("uuid-1");
    }
}
