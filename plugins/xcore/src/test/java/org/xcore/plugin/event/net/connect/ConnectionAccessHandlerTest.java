package org.xcore.plugin.event.net.connect;

import mindustry.net.NetConnection;
import mindustry.net.Packets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.security.ingress.AccessResult;
import org.xcore.plugin.security.ingress.IngressService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionAccessHandlerTest {

    @Test
    @DisplayName("allow returns true when access is granted")
    void allow_returnsTrueWhenGranted() {
        IngressService ingressService = mock(IngressService.class);
        ConnectionAccessHandler handler = new ConnectionAccessHandler(ingressService);

        NetConnection con = mock(NetConnection.class);
        Packets.ConnectPacket packet = new Packets.ConnectPacket();

        when(ingressService.validate(con, packet)).thenReturn(new AccessResult.Allowed());

        boolean allowed = handler.allow(con, packet);

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("allow closes connection on silent deny")
    void allow_closesConnectionOnSilentDeny() {
        IngressService ingressService = mock(IngressService.class);
        ConnectionAccessHandler handler = new ConnectionAccessHandler(ingressService);

        NetConnection con = mock(NetConnection.class);
        Packets.ConnectPacket packet = new Packets.ConnectPacket();

        when(ingressService.validate(con, packet)).thenReturn(new AccessResult.Denied("blocked", true, 0));

        boolean allowed = handler.allow(con, packet);

        assertThat(allowed).isFalse();
        verify(con).close();
    }

    @Test
    @DisplayName("allow kicks connection with reason on regular deny")
    void allow_kicksConnectionOnRegularDeny() {
        IngressService ingressService = mock(IngressService.class);
        ConnectionAccessHandler handler = new ConnectionAccessHandler(ingressService);

        NetConnection con = mock(NetConnection.class);
        Packets.ConnectPacket packet = new Packets.ConnectPacket();

        when(ingressService.validate(con, packet)).thenReturn(new AccessResult.Denied("You are banned", false, 60000));

        boolean allowed = handler.allow(con, packet);

        assertThat(allowed).isFalse();
        verify(con).kick("You are banned", 60000);
    }
}