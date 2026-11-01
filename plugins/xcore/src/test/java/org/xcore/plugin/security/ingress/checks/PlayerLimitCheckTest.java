package org.xcore.plugin.security.ingress.checks;

import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.gen.Groups;
import mindustry.net.Administration;
import mindustry.net.Packets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.security.ingress.AccessResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PlayerLimitCheck")
class PlayerLimitCheckTest {

    private IngressChecksTestSupport.VarsState varsState;
    private Administration admins;
    private TomlXcoreConfig config;
    private PlayerLimitCheck check;

    @BeforeEach
    void setUp() {
        varsState = IngressChecksTestSupport.captureVarsState();

        admins = mock(Administration.class);
        var netServer = mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;
        Groups.player = IngressChecksTestSupport.newPlayerGroup();

        config = new TomlXcoreConfig();
        check = new PlayerLimitCheck(config);
    }

    @AfterEach
    void tearDown() {
        varsState.restore();
    }

    @Test
    @DisplayName("shouldAllow_whenPlayerLimitIsDisabled")
    void shouldAllow_whenPlayerLimitIsDisabled() {
        config.server.playerLimit = 0;
        var packet = IngressChecksTestSupport.newPacket();

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("shouldAllow_whenPlayerIsAdmin")
    void shouldAllow_whenPlayerIsAdmin() {
        config.server.playerLimit = 1;
        var packet = IngressChecksTestSupport.newPacket();
        when(admins.isAdmin(packet.uuid, packet.usid)).thenReturn(true);
        Groups.player.add(IngressChecksTestSupport.createPlayer("A", "uuid-a", "usid-a", false));
        Groups.player.add(IngressChecksTestSupport.createPlayer("B", "uuid-b", "usid-b", false));

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }

    @Test
    @DisplayName("shouldDenyPlayerLimit_whenPlayerCountExceedsNoAdminLimit")
    void shouldDenyPlayerLimit_whenPlayerCountExceedsNoAdminLimit() {
        config.server.playerLimit = 2;
        var packet = IngressChecksTestSupport.newPacket();
        when(admins.isAdmin(packet.uuid, packet.usid)).thenReturn(false);
        Groups.player.add(IngressChecksTestSupport.createPlayer("A", "uuid-a", "usid-a", false));
        Groups.player.add(IngressChecksTestSupport.createPlayer("B", "uuid-b", "usid-b", false));

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isInstanceOfSatisfying(AccessResult.Denied.class,
                denied -> assertThat(denied.reason()).isEqualTo(Packets.KickReason.playerLimit.name()));
    }

    @Test
    @DisplayName("shouldAllow_whenPlayerCountIsWithinLimit")
    void shouldAllow_whenPlayerCountIsWithinLimit() {
        config.server.playerLimit = 2;
        var packet = IngressChecksTestSupport.newPacket();
        when(admins.isAdmin(packet.uuid, packet.usid)).thenReturn(false);
        Groups.player.add(IngressChecksTestSupport.createPlayer("A", "uuid-a", "usid-a", false));

        var result = check.check(new IngressChecksTestSupport.DummyConnection("1.1.1.1"), packet);

        assertThat(result).isSameAs(AccessResult.Allowed.INSTANCE);
    }
}
