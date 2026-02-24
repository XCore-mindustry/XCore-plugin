package org.xcore.plugin.security.ingress.checks;

import mindustry.net.Packets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.security.ingress.AccessResult;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BasicValidationCheck")
class BasicValidationCheckTest {

    private final BasicValidationCheck check = new BasicValidationCheck();

    @Test
    @DisplayName("shouldDenySilently_whenConnectionAlreadyKicked")
    void shouldDenySilently_whenConnectionAlreadyKicked() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        con.kicked = true;
        var packet = IngressChecksTestSupport.newPacket();

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class, denied -> {
            assertThat(denied.reason()).isEqualTo("Already kicked");
            assertThat(denied.silent()).isTrue();
        });
    }

    @Test
    @DisplayName("shouldDeny_whenUuidIsNull")
    void shouldDeny_whenUuidIsNull() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.uuid = null;

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.idInUse.name()));
    }

    @Test
    @DisplayName("shouldDeny_whenUsidIsNull")
    void shouldDeny_whenUsidIsNull() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.usid = null;

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.idInUse.name()));
    }

    @Test
    @DisplayName("shouldDeny_whenConnectionHasBegunConnecting")
    void shouldDeny_whenConnectionHasBegunConnecting() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        con.hasBegunConnecting = true;
        var packet = IngressChecksTestSupport.newPacket();

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.idInUse.name()));
    }

    @Test
    @DisplayName("shouldAllowAndInitializeConnectionFields_whenPacketIsValid")
    void shouldAllowAndInitializeConnectionFields_whenPacketIsValid() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.mobile = true;
        packet.locale = null;

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        assertThat(con.hasBegunConnecting).isTrue();
        assertThat(con.mobile).isTrue();
        assertThat(packet.locale).isEqualTo("en");
    }
}
