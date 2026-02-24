package org.xcore.plugin.security.ingress.checks;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import mindustry.net.Net;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.security.ingress.AccessResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RateLimitCheck")
class RateLimitCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private Administration admins;
    private Net net;
    private RateLimitCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();

        admins = mock(Administration.class);
        var netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;

        net = mock(Net.class);
        Vars.net = net;

        check = new RateLimitCheck();
    }

    @AfterEach
    void tearDown() {
        varsState.restore();
    }

    @Test
    @DisplayName("shouldAllow_whenConnectionsFromIpAreBelowLimit")
    void shouldAllow_whenConnectionsFromIpAreBelowLimit() {
        String ip = "10.0.0.1";
        var con = new IngressChecksTestSupport.DummyConnection(ip);
        var sameIpOne = new IngressChecksTestSupport.DummyConnection(ip);
        var sameIpTwo = new IngressChecksTestSupport.DummyConnection(ip);
        var otherIp = new IngressChecksTestSupport.DummyConnection("10.0.0.2");
        when(net.getConnections()).thenReturn(List.of(sameIpOne, sameIpTwo, otherIp));

        var result = check.check(con, IngressChecksTestSupport.newPacket());

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        verify(admins, never()).blacklistDos(anyString());
    }

    @Test
    @DisplayName("shouldDenySilentlyAndBlacklistAndCloseConnections_whenConnectionsFromIpReachLimit")
    void shouldDenySilentlyAndBlacklistAndCloseConnections_whenConnectionsFromIpReachLimit() {
        String ip = "10.0.0.1";
        var con = new IngressChecksTestSupport.DummyConnection(ip);
        var sameIpOne = new IngressChecksTestSupport.DummyConnection(ip);
        var sameIpTwo = new IngressChecksTestSupport.DummyConnection(ip);
        var sameIpThree = new IngressChecksTestSupport.DummyConnection(ip);
        var otherIp = new IngressChecksTestSupport.DummyConnection("10.0.0.2");
        when(net.getConnections()).thenReturn(List.of(sameIpOne, sameIpTwo, sameIpThree, otherIp));

        var result = check.check(con, IngressChecksTestSupport.newPacket());

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied -> {
            assertThat(denied.reason()).isEqualTo("Too many connections");
            assertThat(denied.silent()).isTrue();
        });
        verify(admins).blacklistDos(ip);
        assertThat(sameIpOne.closeCalls).isEqualTo(1);
        assertThat(sameIpTwo.closeCalls).isEqualTo(1);
        assertThat(sameIpThree.closeCalls).isEqualTo(1);
        assertThat(otherIp.closeCalls).isZero();
    }
}
