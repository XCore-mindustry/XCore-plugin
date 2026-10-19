package org.xcore.plugin.event.net.admin;

import com.google.gson.Gson;
import mindustry.gen.AdminRequestCallPacket;
import mindustry.gen.Player;
import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.integration.AdminModIntegration;
import org.xcore.plugin.permission.PermissionService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.BanMenu;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The admin menu of the client: who may use it, and on whom. */
class AdminRequestHandlerTest {

    private SessionService sessionService;
    private BanMenu banMenu;
    private AdminRequestHandler handler;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionService.class);
        banMenu = mock(BanMenu.class);
        handler = new AdminRequestHandler(sessionService, banMenu, mock(AdminModIntegration.class), new Gson(),
                new PermissionService());
    }

    private static Player player(String uuid, boolean admin) {
        Player player = mock(Player.class);
        when(player.uuid()).thenReturn(uuid);
        when(player.plainName()).thenReturn(uuid);
        when(player.coloredName()).thenReturn(uuid);
        player.admin = admin;
        player.con = mock(NetConnection.class);
        player.con.player = player;
        return player;
    }

    private void request(Player from, Player target, Packets.AdminAction action) {
        AdminRequestCallPacket packet = new AdminRequestCallPacket();
        packet.other = target;
        packet.action = action;
        handler.handle(from.con, packet);
    }

    @Test
    @DisplayName("An admin kicks a player")
    void adminKicksPlayer() {
        Player target = player("target", false);

        request(player("admin", true), target, Packets.AdminAction.kick);

        verify(target).kick(Packets.KickReason.kick);
        verify(sessionService).broadcast(anyString(), any());
    }

    @Test
    @DisplayName("A packet from a player who is not an admin is ignored, whatever the action")
    void playerIsIgnored() {
        Player target = player("target", false);
        Player sender = player("player", false);

        for (Packets.AdminAction action : Packets.AdminAction.values()) {
            request(sender, target, action);
        }

        verify(target, never()).kick(any(Packets.KickReason.class));
        verifyNoInteractions(sessionService, banMenu);
    }

    @Test
    @DisplayName("An admin cannot act on another admin")
    void adminCannotTargetAdmin() {
        Player target = player("other-admin", true);
        Player admin = player("admin", true);

        request(admin, target, Packets.AdminAction.kick);
        request(admin, target, Packets.AdminAction.ban);

        verify(target, never()).kick(any(Packets.KickReason.class));
        verifyNoInteractions(sessionService, banMenu);
    }

    @Test
    @DisplayName("An admin can act on themselves, as before")
    void adminCanTargetSelf() {
        Player admin = player("admin", true);

        request(admin, admin, Packets.AdminAction.kick);

        verify(admin).kick(Packets.KickReason.kick);
    }
}
