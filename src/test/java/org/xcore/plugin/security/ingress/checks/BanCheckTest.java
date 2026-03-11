package org.xcore.plugin.security.ingress.checks;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.localization.LocalizationFactory;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.security.ingress.AccessResult;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BanCheck")
class BanCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private Administration admins;
    private BanDataRepository banDataRepository;
    private BanCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();

        admins = mock(Administration.class);
        var netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;

        banDataRepository = mock(BanDataRepository.class);
        var bundle = IngressChecksTestSupport.mockBundleService();
        var globalConfig = new GlobalConfig();
        globalConfig.discordUrl = "https://discord.example";

        check = new BanCheck(banDataRepository, bundle, new LocalizationFactory(() -> bundle), globalConfig);
    }

    @AfterEach
    void tearDown() {
        varsState.restore();
    }

    @Test
    @DisplayName("shouldAllow_whenNoActiveBanAndNotBannedInAdmins")
    void shouldAllow_whenNoActiveBanAndNotBannedInAdmins() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        when(banDataRepository.find(packet.uuid, con.address)).thenReturn(null);
        when(admins.isIPBanned(con.address)).thenReturn(false);
        when(admins.isSubnetBanned(con.address)).thenReturn(false);
        when(admins.isIDBanned(packet.uuid)).thenReturn(false);

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("shouldUnbanAndDeleteAndAllow_whenBanIsExpired")
    void shouldUnbanAndDeleteAndAllow_whenBanIsExpired() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        var ban = BanData.builder()
                .uuid(packet.uuid)
                .ip(con.address)
                .name("Player")
                .adminName("Admin")
                .reason("Reason")
                .expireDate(Instant.now().minusSeconds(1))
                .build();
        when(banDataRepository.find(packet.uuid, con.address)).thenReturn(ban);

        var result = check.check(con, packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        verify(admins).unbanPlayerID(packet.uuid);
        verify(admins).unbanPlayerIP(con.address);
        verify(banDataRepository).delete(packet.uuid, con.address);
    }

    @Test
    @DisplayName("shouldDenyWithReason_whenBanIsActive")
    void shouldDenyWithReason_whenBanIsActive() {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        var ban = BanData.builder()
                .uuid(packet.uuid)
                .ip(con.address)
                .name("Player")
                .adminName("Admin")
                .reason("griefing")
                .expireDate(Instant.now().plusSeconds(3600))
                .build();
        when(banDataRepository.find(packet.uuid, con.address)).thenReturn(ban);

        // Surface the reason field directly in test output to validate formatting arguments.
        var bundle = IngressChecksTestSupport.mockBundleService();
        when(bundle.format(any(Locale.class), eq("tempban-content"), anyMap()))
                .thenAnswer(invocation -> "tempban: " + ((Map<?, ?>) invocation.getArgument(2)).get("reason"));
        check = new BanCheck(banDataRepository, bundle, new LocalizationFactory(() -> bundle), new GlobalConfig());

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).contains("griefing"));
    }

    @Test
    @DisplayName("shouldDeny_whenIpIsBannedInAdmins")
    void shouldDeny_whenIpIsBannedInAdmins() {
        assertDeniedWhenAdminsBanState(true, false, false);
    }

    @Test
    @DisplayName("shouldDeny_whenSubnetIsBannedInAdmins")
    void shouldDeny_whenSubnetIsBannedInAdmins() {
        assertDeniedWhenAdminsBanState(false, true, false);
    }

    @Test
    @DisplayName("shouldDeny_whenIdIsBannedInAdmins")
    void shouldDeny_whenIdIsBannedInAdmins() {
        assertDeniedWhenAdminsBanState(false, false, true);
    }

    private void assertDeniedWhenAdminsBanState(boolean ipBanned, boolean subnetBanned, boolean idBanned) {
        var con = new IngressChecksTestSupport.DummyConnection("1.1.1.1");
        var packet = IngressChecksTestSupport.newPacket();
        when(banDataRepository.find(packet.uuid, con.address)).thenReturn(null);
        when(admins.isIPBanned(con.address)).thenReturn(ipBanned);
        when(admins.isSubnetBanned(con.address)).thenReturn(subnetBanned);
        when(admins.isIDBanned(packet.uuid)).thenReturn(idBanned);

        var result = check.check(con, packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo("ban-content"));
    }
}
