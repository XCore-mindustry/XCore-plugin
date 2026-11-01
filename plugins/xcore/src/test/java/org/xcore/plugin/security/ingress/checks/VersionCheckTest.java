package org.xcore.plugin.security.ingress.checks;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.core.Version;
import mindustry.net.Administration;
import mindustry.net.Packets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.security.ingress.AccessResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("VersionCheck")
class VersionCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private String versionType;
    private int versionBuild;

    private Administration admins;
    private VersionCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();
        versionType = Version.type;
        versionBuild = Version.build;

        admins = mock(Administration.class);
        var netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;

        Version.type = "release";
        Version.build = 100;
        check = new VersionCheck();
    }

    @AfterEach
    void tearDown() {
        varsState.restore();
        Version.type = versionType;
        Version.build = versionBuild;
    }

    @Test
    @DisplayName("shouldDenyTypeMismatch_whenVersionTypeDiffers")
    void shouldDenyTypeMismatch_whenVersionTypeDiffers() {
        when(admins.allowsCustomClients()).thenReturn(false);
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.versionType = "bleeding-edge";

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.typeMismatch.name()));
    }

    @Test
    @DisplayName("shouldDenyServerOutdated_whenClientBuildIsHigher")
    void shouldDenyServerOutdated_whenClientBuildIsHigher() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.version = Version.build + 1;
        packet.versionType = Version.type;

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.serverOutdated.name()));
    }

    @Test
    @DisplayName("shouldDenyClientOutdated_whenClientBuildIsLower")
    void shouldDenyClientOutdated_whenClientBuildIsLower() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.version = Version.build - 1;
        packet.versionType = Version.type;

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.clientOutdated.name()));
    }

    @Test
    @DisplayName("shouldAllowAndSetModClient_whenVersionIsMinusOneAndCustomClientsAllowed")
    void shouldAllowAndSetModClient_whenVersionIsMinusOneAndCustomClientsAllowed() {
        when(admins.allowsCustomClients()).thenReturn(true);
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.version = -1;
        packet.versionType = Version.type;

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        assertThat(con.modclient).isTrue();
    }

    @Test
    @DisplayName("shouldAllow_whenVersionsAreValid")
    void shouldAllow_whenVersionsAreValid() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        packet.version = Version.build;
        packet.versionType = Version.type;

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        assertThat(con.modclient).isFalse();
    }
}
