package org.xcore.plugin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.protocol.generated.messages.server.ServerMessages.ServerHeartbeatV1;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.service.ServerRegistryService.Category;
import org.xcore.plugin.service.ServerRegistryService.ServerStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ServerRegistryServiceTest {

    private TomlXcoreConfig createConfig(String serverName) {
        TomlXcoreConfig config = new TomlXcoreConfig();
        config.server.name = serverName;
        config.server.playerLimit = 25;
        return config;
    }

    @Test
    @DisplayName("snapshot correctly flags current server and sorts it first")
    void snapshot_flagsCurrentServerAndSortsFirst() {
        TomlXcoreConfig config = createConfig("mini-pvp");
        NetworkService network = mock(NetworkService.class);
        ServerRegistryService service = new ServerRegistryService(config, network);

        List<ServerStatus> servers = service.snapshot();
        assertThat(servers).isNotEmpty();

        ServerStatus first = servers.get(0);
        assertThat(first.isCurrent()).isTrue();
        assertThat(first.template().id()).isEqualTo("mini-pvp");
        assertThat(first.online()).isTrue();
    }

    @Test
    @DisplayName("heartbeat updates remote server status, players, host, and port")
    void handleHeartbeat_updatesRemoteServer() {
        TomlXcoreConfig config = createConfig("mini-pvp");
        NetworkService network = mock(NetworkService.class);
        ServerRegistryService service = new ServerRegistryService(config, network);

        // Send heartbeat for mini-surv
        ServerHeartbeatV1 hb = new ServerHeartbeatV1(
                "mini-surv",
                123456789L,
                15,
                20,
                "v160",
                "45.136.205.10",
                7002
        );
        service.handleHeartbeat(hb);

        Optional<ServerStatus> survOpt = service.findServer("mini-surv");
        assertThat(survOpt).isPresent();
        ServerStatus surv = survOpt.get();

        assertThat(surv.online()).isTrue();
        assertThat(surv.onlinePlayers()).isEqualTo(15);
        assertThat(surv.maxPlayers()).isEqualTo(20);
        assertThat(surv.host()).isEqualTo("45.136.205.10");
        assertThat(surv.template().port()).isEqualTo(7002);
        assertThat(surv.isFull()).isFalse();

        // Verify capacity bar
        assertThat(surv.capacityBar()).contains("■■■■");
    }

    @Test
    @DisplayName("capacityBar returns appropriate color and block counts")
    void capacityBar_calculatesBlocks() {
        ServerRegistryService.ServerTemplate tmpl = new ServerRegistryService.ServerTemplate(
                "test", "Test", Category.PVP, "⚔", "ff5555", "PVP", 7001, 20
        );

        ServerStatus half = new ServerStatus(tmpl, 10, 20, true, false, "-", null, 60, 20, "host", System.currentTimeMillis());
        assertThat(half.capacityBar()).contains("■■■");

        ServerStatus full = new ServerStatus(tmpl, 20, 20, true, false, "-", null, 60, 20, "host", System.currentTimeMillis());
        assertThat(full.capacityBar()).contains("[scarlet]");
        assertThat(full.capacityBar()).contains("■■■■■");

        ServerStatus offline = new ServerStatus(tmpl, 0, 20, false, false, "-", null, 0, 0, "host", 0);
        assertThat(offline.capacityBar()).isEqualTo("[darkgray]□□□□□[]");
    }

    @Test
    @DisplayName("findServer resolves by ID or display name ignoring case")
    void findServer_resolvesByIdOrName() {
        TomlXcoreConfig config = createConfig("mini-pvp");
        NetworkService network = mock(NetworkService.class);
        ServerRegistryService service = new ServerRegistryService(config, network);

        assertThat(service.findServer("mini-attack")).isPresent();
        assertThat(service.findServer("MINI-ATTACK")).isPresent();
        assertThat(service.findServer("The Siege")).isPresent();
        assertThat(service.findServer("unknown-server-xyz")).isEmpty();
    }
}
