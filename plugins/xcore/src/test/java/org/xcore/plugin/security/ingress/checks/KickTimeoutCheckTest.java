package org.xcore.plugin.security.ingress.checks;

import arc.util.Time;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.security.ingress.AccessResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("KickTimeoutCheck")
class KickTimeoutCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private Administration admins;
    private KickTimeoutCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();
        admins = mock(Administration.class);
        var netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;

        var bundle = IngressChecksTestSupport.testBundle();
        check = new KickTimeoutCheck(bundle);
    }

    @AfterEach
    void tearDown() {
        varsState.restore();
    }

    @Test
    @DisplayName("shouldDeny_whenKickTimeoutIsStillActive")
    void shouldDeny_whenKickTimeoutIsStillActive() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        when(admins.getKickTime(packet.uuid, con.address)).thenReturn(Time.millis() + 75_000);

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied -> {
            assertThat(denied.silent()).isFalse();
            assertThat(denied.reason()).isEqualTo("kick-recently-kicked");
        });
    }

    @Test
    @DisplayName("shouldAllow_whenKickTimeoutHasExpired")
    void shouldAllow_whenKickTimeoutHasExpired() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        when(admins.getKickTime(packet.uuid, con.address)).thenReturn(Time.millis() - 1);

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }
}
