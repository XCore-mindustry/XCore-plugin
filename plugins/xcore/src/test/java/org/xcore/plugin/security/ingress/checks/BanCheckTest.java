package org.xcore.plugin.security.ingress.checks;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ospx.flubundle.Bundle;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.security.ingress.AccessResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BanCheck")
class BanCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private Administration admins;
    private BanDataRepository banDataRepository;
    private Async async;
    private BanCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();

        admins = mock(Administration.class);
        var netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;

        // Ingress runs on an executor; a synchronous dispatcher keeps these assertions
        // about ban semantics rather than about scheduling.
        async = new Async(new StorageExecutor(4), Runnable::run);
        banDataRepository = mock(BanDataRepository.class);
        var bundle = IngressChecksTestSupport.testBundle();
        var secretsConfig = new TomlSecretsConfig();
        secretsConfig.externalLinks.discordUrl = "https://discord.example";

        check = new BanCheck(banDataRepository, bundle, secretsConfig, async);
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
    @DisplayName("expiredBan_doesNotTouchAdminRegistryFromIngressThread")
    void expiredBan_doesNotTouchAdminRegistryFromIngressThread() {
        // Stand in for a game thread that has not drained yet. The ingress executor is
        // not the game thread, so the registry write must be waiting, not applied inline.
        List<Runnable> marshalled = new ArrayList<>();
        check = new BanCheck(banDataRepository, IngressChecksTestSupport.testBundle(),
                new TomlSecretsConfig(), new Async(new StorageExecutor(4), marshalled::add));

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

        // The decision must not wait on the registry write: the ban is expired either way.
        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
        assertThat(marshalled).as("registry write must be queued for the game thread").hasSize(1);
        verify(admins, never()).unbanPlayerID(anyString());
        verify(admins, never()).unbanPlayerIP(anyString());

        marshalled.forEach(Runnable::run);

        verify(admins).unbanPlayerID(packet.uuid);
        verify(admins).unbanPlayerIP(con.address);
        // The row delete is not game state, so it stays on the ingress thread.
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
        Bundle bundle = new Bundle(Locale.ENGLISH) {
            @Override
            public Locale resolveLocale(String code) {
                return Locale.ENGLISH;
            }

            @Override
            public Locale resolveLocale(Locale locale) {
                return locale == null ? Locale.ENGLISH : locale;
            }

            @Override
            public String format(Locale locale, String id, Map<String, Object> args) {
                if ("tempban-content".equals(id)) {
                    return "tempban: " + args.get("reason");
                }
                return id;
            }
        };
        check = new BanCheck(banDataRepository, bundle, new TomlSecretsConfig(), async);

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
