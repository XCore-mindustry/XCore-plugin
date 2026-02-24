package org.xcore.plugin.security.ingress.checks;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import mindustry.net.Net;
import mindustry.net.Packets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.security.ingress.AccessResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DuplicateCheck")
class DuplicateCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private Administration admins;
    private Net net;
    private DuplicateCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();

        admins = mock(Administration.class);
        var netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;

        net = mock(Net.class);
        Vars.net = net;
        Groups.player = IngressChecksTestSupport.newPlayerGroup();

        check = new DuplicateCheck();
    }

    @AfterEach
    void tearDown() {
        varsState.restore();
    }

    @Test
    @DisplayName("shouldAllow_whenStrictModeIsDisabled")
    void shouldAllow_whenStrictModeIsDisabled() {
        when(admins.isStrict()).thenReturn(false);
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("shouldDenyNameInUse_whenPlayerNameAlreadyExists")
    void shouldDenyNameInUse_whenPlayerNameAlreadyExists() {
        when(admins.isStrict()).thenReturn(true);
        var packet = IngressChecksTestSupport.newPacket();
        packet.name = "[green]alice";
        Groups.player.add(IngressChecksTestSupport.createPlayer("Alice", "uuid-a", "usid-a", false));

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.nameInUse.name()));
    }

    @Test
    @DisplayName("shouldDenyIdInUse_whenUuidOrUsidDuplicateExistsInPlayerGroup")
    void shouldDenyIdInUse_whenUuidOrUsidDuplicateExistsInPlayerGroup() {
        when(admins.isStrict()).thenReturn(true);
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.uuid = "dup-uuid";
        packet.usid = "new-usid";
        Groups.player.add(IngressChecksTestSupport.createPlayer("Bob", "dup-uuid", "old-usid", false));

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.idInUse.name()));
        assertThat(con.uuid).isEqualTo("dup-uuid");
    }

    @Test
    @DisplayName("shouldDenyIdInUse_whenUuidDuplicateExistsInConnections")
    void shouldDenyIdInUse_whenUuidDuplicateExistsInConnections() {
        when(admins.isStrict()).thenReturn(true);
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.uuid = "dup-uuid";
        packet.usid = "usid-new";

        var otherCon = new IngressChecksTestSupport.DummyConnection("2.2.2.2");
        otherCon.uuid = "dup-uuid";
        when(net.getConnections()).thenReturn(List.of(otherCon));

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.idInUse.name()));
        assertThat(con.uuid).isEqualTo("dup-uuid");
    }

    @Test
    @DisplayName("shouldAllow_whenNoDuplicatesFound")
    void shouldAllow_whenNoDuplicatesFound() {
        when(admins.isStrict()).thenReturn(true);
        when(net.getConnections()).thenReturn(List.of());
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.uuid = "unique-uuid";
        packet.usid = "unique-usid";
        packet.name = "UniqueName";

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }
}
