package org.xcore.plugin.security.ingress.checks;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import mindustry.net.Packets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.security.ingress.AccessResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("WhitelistCheck")
class WhitelistCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private Administration admins;
    private WhitelistCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();
        admins = mock(Administration.class);
        var netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;
        check = new WhitelistCheck();
    }

    @AfterEach
    void tearDown() {
        varsState.restore();
    }

    @Test
    @DisplayName("shouldAllow_whenPlayerIsWhitelisted")
    void shouldAllow_whenPlayerIsWhitelisted() {
        var packet = IngressChecksTestSupport.newPacket();
        when(admins.isWhitelisted(packet.uuid, packet.usid)).thenReturn(true);

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("shouldDenyWhitelistAndUpdatePlayerInfo_whenPlayerIsNotWhitelisted")
    void shouldDenyWhitelistAndUpdatePlayerInfo_whenPlayerIsNotWhitelisted() {
        var packet = IngressChecksTestSupport.newPacket();
        var info = new Administration.PlayerInfo();
        when(admins.isWhitelisted(packet.uuid, packet.usid)).thenReturn(false);
        when(admins.getInfo(packet.uuid)).thenReturn(info);

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.whitelist.name()));
        assertThat(info.adminUsid).isEqualTo(packet.usid);
        assertThat(info.lastName).isEqualTo(packet.name);
        assertThat(info.id).isEqualTo(packet.uuid);
        verify(admins).save();
    }
}
