package org.xcore.plugin.security.ingress.checks;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Packets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.security.ingress.AccessResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("NameValidationCheck")
class NameValidationCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private NetServer netServer;
    private NameValidationCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();
        netServer = mock(NetServer.class);
        Vars.netServer = netServer;

        BundleService bundle = IngressChecksTestSupport.mockBundleService();
        check = new NameValidationCheck(bundle);
    }

    @AfterEach
    void tearDown() {
        varsState.restore();
    }

    @Test
    @DisplayName("shouldDenyPiratedName_whenNameIsInBannedList")
    void shouldDenyPiratedName_whenNameIsInBannedList() {
        var packet = IngressChecksTestSupport.newPacket();
        packet.name = "Valve";

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo("kick-pirated-game"));
    }

    @Test
    @DisplayName("shouldDenyNameEmpty_whenFixedNameIsBlank")
    void shouldDenyNameEmpty_whenFixedNameIsBlank() {
        var packet = IngressChecksTestSupport.newPacket();
        packet.name = "%%%";
        when(netServer.fixName(packet.name)).thenReturn("   ");

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.nameEmpty.name()));
    }

    @Test
    @DisplayName("shouldAllowAndNormalizeName_whenNameIsValid")
    void shouldAllowAndNormalizeName_whenNameIsValid() {
        var packet = IngressChecksTestSupport.newPacket();
        packet.name = " raw ";
        when(netServer.fixName(packet.name)).thenReturn("Normalized");

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        assertThat(packet.name).isEqualTo("Normalized");
    }
}
