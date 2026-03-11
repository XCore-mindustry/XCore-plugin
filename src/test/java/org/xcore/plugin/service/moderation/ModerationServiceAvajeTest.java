package org.xcore.plugin.service.moderation;

import io.avaje.inject.BeanScope;
import io.avaje.inject.spi.AvajeModule;
import io.avaje.inject.spi.Builder;
import mindustry.Vars;
import mindustry.core.NetServer;
import mindustry.net.Administration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.session.SessionService;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ModerationServiceAvajeTest {

    private BeanScope scope;
    private ModerationService moderationService;
    private NetServer previousNetServer;

    private PlayerDataRepository playerDataRepository;
    private BanDataRepository banDataRepository;
    private MuteDataRepository muteDataRepository;
    private SessionService sessionService;
    private NetworkService network;
    private FindService find;
    private TimeService time;
    private Administration admins;

    @BeforeEach
    void setUp() {
        previousNetServer = Vars.netServer;
        admins = org.mockito.Mockito.mock(Administration.class);
        var netServer = org.mockito.Mockito.mock(NetServer.class);
        netServer.admins = admins;
        Vars.netServer = netServer;

        // We intentionally avoid @InjectTest/TestBeanScope here because global test scope
        // would bootstrap the full Xcore module graph and fail on Vars.dataDirectory.
        // This local module keeps the Avaje context minimal and deterministic.
        scope = BeanScope.builder()
                .modules(new ModerationServiceModule())
                .forTesting()
                .mock(PlayerDataRepository.class)
                .mock(BanDataRepository.class)
                .mock(MuteDataRepository.class)
                .mock(SessionService.class)
                .mock(NetworkService.class)
                .mock(FindService.class)
                .mock(TimeService.class)
                .build();

        moderationService = scope.get(ModerationService.class);
        playerDataRepository = scope.get(PlayerDataRepository.class);
        banDataRepository = scope.get(BanDataRepository.class);
        muteDataRepository = scope.get(MuteDataRepository.class);
        sessionService = scope.get(SessionService.class);
        network = scope.get(NetworkService.class);
        find = scope.get(FindService.class);
        time = scope.get(TimeService.class);
    }

    @AfterEach
    void tearDown() {
        scope.close();
        Vars.netServer = previousNetServer;
    }

    @Test
    @DisplayName("tempBanByUuidOrIp fails when both UUID and IP are missing")
    void tempBanFailsWithoutIdentifiers() {
        var result = moderationService.tempBanByUuidOrIp(null, null, "name", Duration.ofMinutes(10), "reason", "admin");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Either UUID or IP must be provided");
        verifyNoInteractions(banDataRepository, network);
    }

    @Test
    @DisplayName("tempBanByUuidOrIp saves ban and posts kick with defaults")
    void tempBanSuccess() {
        var duration = Duration.ofHours(2);
        var before = Instant.now();
        when(banDataRepository.save(any())).thenReturn(true);

        var result = moderationService.tempBanByUuidOrIp("uuid-1", "1.2.3.4", null, duration, null, "admin");

        var after = Instant.now();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isPresent();
        assertThat(result.getData().orElseThrow().getName()).isEqualTo("Unknown");
        assertThat(result.getData().orElseThrow().getReason()).isEqualTo("Not Specified");

        var order = inOrder(banDataRepository, network);
        order.verify(banDataRepository).save(any(BanData.class));
        order.verify(network).post(argThat(event ->
                event instanceof BanData ban
                        && "uuid-1".equals(ban.getUuid())
                        && "1.2.3.4".equals(ban.getIp())
                        && "admin".equals(ban.getAdminName())
                        && "Unknown".equals(ban.getName())
                        && "Not Specified".equals(ban.getReason())
                        && !ban.getExpireDate().isBefore(before.plus(duration))
                        && !ban.getExpireDate().isAfter(after.plus(duration))));
        order.verify(network).post(argThat(event ->
                event instanceof SocketEvents.KickBannedPlayer kick
                        && "uuid-1".equals(kick.uuid())
                        && "1.2.3.4".equals(kick.ip())));

        verify(banDataRepository).save(argThat(ban ->
                "uuid-1".equals(ban.getUuid())
                        && "1.2.3.4".equals(ban.getIp())
                        && "Unknown".equals(ban.getName())));
    }

    @Test
    @DisplayName("tempBanByUuidOrIp fails when ban persistence fails")
    void tempBanFailsWhenSaveFails() {
        when(banDataRepository.save(any())).thenReturn(false);

        var result = moderationService.tempBanByUuidOrIp("uuid-1", null, "name", Duration.ofMinutes(10), "reason", "admin");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Failed to save ban");
        verify(banDataRepository).save(any(BanData.class));
        verifyNoInteractions(network);
    }

    @Test
    @DisplayName("tempUnban fails when both UUID and IP are missing")
    void tempUnbanFailsWithoutIdentifiers() {
        var result = moderationService.tempUnban(null, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Either UUID or IP must be provided");
        verifyNoInteractions(banDataRepository);
    }

    @Test
    @DisplayName("tempUnban deletes ban by provided identifiers")
    void tempUnbanSuccess() {
        when(banDataRepository.delete("uuid-2", null)).thenReturn(true);

        var result = moderationService.tempUnban("uuid-2", null);

        assertThat(result.isSuccess()).isTrue();
        verify(banDataRepository).delete("uuid-2", null);
    }

    @Test
    @DisplayName("tempUnban fails when ban delete does not remove anything")
    void tempUnbanFailsWhenDeleteFails() {
        when(banDataRepository.delete("uuid-2", null)).thenReturn(false);

        var result = moderationService.tempUnban("uuid-2", null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Failed to delete ban");
    }

    @Test
    @DisplayName("banById fails when player is not found")
    void banById_notFound_returnsFailure() {
        when(playerDataRepository.findByPid(404)).thenReturn(null);

        var result = moderationService.banById(404, "admin", "reason", Duration.ofMinutes(10), true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Player not found");
        verifyNoInteractions(banDataRepository, network);
    }

    @Test
    @DisplayName("unbanById fails when player is not found")
    void unbanById_notFound_returnsFailure() {
        when(playerDataRepository.findByPid(405)).thenReturn(null);

        var result = moderationService.unbanById(405);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Player not found");
        verify(banDataRepository, never()).delete(any(), any());
    }

    @Test
    @DisplayName("muteById fails when player is not found")
    void muteByIdPlayerNotFound() {
        when(sessionService.getOrLoadFromDb(100)).thenReturn(null);

        var result = moderationService.muteById(100, "admin", "reason", Duration.ofMinutes(30));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Player not found");
        verifyNoInteractions(muteDataRepository, network);
    }

    @Test
    @DisplayName("muteById stores mute and posts event")
    void muteByIdSuccess() {
        var target = PlayerData.builder()
                .uuid("uuid-3")
                .nickname("Target")
                .build();
        when(sessionService.getOrLoadFromDb(7)).thenReturn(target);
        when(muteDataRepository.save(any())).thenReturn(true);
        var duration = Duration.ofMinutes(15);
        var before = Instant.now();

        var result = moderationService.muteById(7, "admin", null, duration);

        var after = Instant.now();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isPresent();

        verify(muteDataRepository).save(argThat(mute ->
                "uuid-3".equals(mute.getUuid())
                        && "Target".equals(mute.getName())
                        && "admin".equals(mute.getAdminName())
                        && "Not Specified".equals(mute.getReason())
                        && !mute.getExpireDate().isBefore(before.plus(duration))
                        && !mute.getExpireDate().isAfter(after.plus(duration))));

        var order = inOrder(muteDataRepository, network);
        order.verify(muteDataRepository).save(any(MuteData.class));
        order.verify(network).post(argThat(event ->
                event instanceof MuteData mute && "uuid-3".equals(mute.getUuid())));
    }

    @Test
    @DisplayName("muteById fails when mute persistence fails")
    void muteByIdFailsWhenSaveFails() {
        var target = PlayerData.builder()
                .uuid("uuid-3")
                .nickname("Target")
                .build();
        when(sessionService.getOrLoadFromDb(7)).thenReturn(target);
        when(muteDataRepository.save(any())).thenReturn(false);

        var result = moderationService.muteById(7, "admin", null, Duration.ofMinutes(15));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Failed to save mute");
        verifyNoInteractions(network);
    }

    @Test
    @DisplayName("unmuteById deletes mute when player exists")
    void unmuteByIdSuccess() {
        var target = PlayerData.builder()
                .uuid("uuid-4")
                .nickname("Target2")
                .build();
        when(sessionService.getOrLoadFromDb(8)).thenReturn(target);
        when(muteDataRepository.delete("uuid-4")).thenReturn(true);

        var result = moderationService.unmuteById(8);

        assertThat(result.isSuccess()).isTrue();
        verify(muteDataRepository).delete("uuid-4");
    }

    @Test
    @DisplayName("unmuteById fails when mute delete does not remove anything")
    void unmuteByIdFailsWhenDeleteFails() {
        var target = PlayerData.builder()
                .uuid("uuid-4")
                .nickname("Target2")
                .build();
        when(sessionService.getOrLoadFromDb(8)).thenReturn(target);
        when(muteDataRepository.delete("uuid-4")).thenReturn(false);

        var result = moderationService.unmuteById(8);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Failed to delete mute");
    }

    @Test
    @DisplayName("banById saves before side effects")
    void banByIdSavesBeforeSideEffects() {
        var target = PlayerData.builder()
                .pid(9)
                .uuid("uuid-9")
                .nickname("Target9")
                .build();
        when(playerDataRepository.findByPid(9)).thenReturn(target);
        when(banDataRepository.save(any())).thenReturn(true);

        var result = moderationService.banById(9, "admin", null, Duration.ofMinutes(10), true);

        assertThat(result.isSuccess()).isTrue();
        var order = inOrder(banDataRepository, network);
        order.verify(banDataRepository).save(any(BanData.class));
        order.verify(network).post(any(BanData.class));
        order.verify(network).post(argThat(event -> event instanceof SocketEvents.KickBannedPlayer));
    }

    @Test
    @DisplayName("banById fails when ban persistence fails")
    void banByIdFailsWhenSaveFails() {
        var target = PlayerData.builder()
                .pid(9)
                .uuid("uuid-9")
                .nickname("Target9")
                .build();
        when(playerDataRepository.findByPid(9)).thenReturn(target);
        when(banDataRepository.save(any())).thenReturn(false);

        var result = moderationService.banById(9, "admin", null, Duration.ofMinutes(10), true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Failed to save ban");
        verifyNoInteractions(network);
    }

    @Test
    @DisplayName("unbanById fails when ban delete does not remove anything")
    void unbanByIdFailsWhenDeleteFails() {
        var target = PlayerData.builder()
                .pid(10)
                .uuid("uuid-10")
                .nickname("Target10")
                .build();
        when(playerDataRepository.findByPid(10)).thenReturn(target);
        when(banDataRepository.delete("uuid-10", null)).thenReturn(false);

        var result = moderationService.unbanById(10);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Failed to delete ban");
    }

    @Test
    @DisplayName("parsePeriod delegates to TimeService")
    void parsePeriodDelegation() {
        when(time.parsePeriod("90", TimeUnit.MINUTES)).thenReturn(Instant.ofEpochMilli(5_000));

        var result = moderationService.parsePeriod("90", TimeUnit.MINUTES);

        assertThat(result).isEqualTo(Duration.ofMillis(5_000));
        verify(time).parsePeriod("90", TimeUnit.MINUTES);
    }

    @Test
    @DisplayName("parsePeriod returns null when TimeService cannot parse value")
    void parsePeriod_whenTimeServiceReturnsNull_returnsNull() {
        when(time.parsePeriod("bad", TimeUnit.DAYS)).thenReturn(null);

        var result = moderationService.parsePeriod("bad", TimeUnit.DAYS);

        assertThat(result).isNull();
        verify(time).parsePeriod("bad", TimeUnit.DAYS);
    }

    @Test
    @DisplayName("findPlayerData delegates to FindService")
    void findPlayerDataDelegation() {
        var data = PlayerData.builder().uuid("uuid-5").nickname("Nick").build();
        when(find.playerData("#12")).thenReturn(data);

        var result = moderationService.findPlayerData("#12");

        assertThat(result).isSameAs(data);
        verify(find).playerData("#12");
    }

    @Test
    @DisplayName("findPlayerData returns null when FindService returns null")
    void findPlayerData_whenFindReturnsNull_returnsNull() {
        when(find.playerData("unknown")).thenReturn(null);

        var result = moderationService.findPlayerData("unknown");

        assertThat(result).isNull();
        verify(find).playerData("unknown");
    }

    private static final class ModerationServiceModule implements AvajeModule {
        @Override
        public Class<?>[] classes() {
            return new Class<?>[]{ModerationService.class};
        }

        @Override
        public void build(Builder builder) {
            if (builder.isBeanAbsent(ModerationService.class)) {
                builder.register(new ModerationService(
                        builder.get(PlayerDataRepository.class),
                        builder.get(BanDataRepository.class),
                        builder.get(MuteDataRepository.class),
                        builder.get(SessionService.class),
                        builder.get(NetworkService.class),
                        builder.get(FindService.class),
                        builder.get(TimeService.class)
                ));
            }
        }
    }
}
