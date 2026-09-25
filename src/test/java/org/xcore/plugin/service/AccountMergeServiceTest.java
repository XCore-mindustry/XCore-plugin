package org.xcore.plugin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.model.*;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.server.ServerMessages.PlayerDataCacheReloadCommandV1;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountMergeServiceTest {

    private PlayerDataRepository playerDataRepository;
    private GameDataRepository gameDataRepository;
    private BanDataRepository banDataRepository;
    private MuteDataRepository muteDataRepository;
    private AuditService auditService;
    private SessionService sessionService;
    private PlayerDisplayService playerDisplayService;
    private NetworkService networkService;
    private FindService findService;
    private TopMenuCacheService topMenuCacheService;
    private TomlXcoreConfig config;

    private AccountMergeService service;

    @BeforeEach
    void setUp() {
        playerDataRepository = mock(PlayerDataRepository.class);
        gameDataRepository = mock(GameDataRepository.class);
        banDataRepository = mock(BanDataRepository.class);
        muteDataRepository = mock(MuteDataRepository.class);
        auditService = mock(AuditService.class);
        sessionService = mock(SessionService.class);
        playerDisplayService = mock(PlayerDisplayService.class);
        networkService = mock(NetworkService.class);
        findService = mock(FindService.class);
        topMenuCacheService = mock(TopMenuCacheService.class);
        config = new TomlXcoreConfig();
        config.server.name = "test-server";

        when(playerDataRepository.save(any(PlayerData.class))).thenReturn(true);

        service = new AccountMergeService(
                playerDataRepository,
                gameDataRepository,
                banDataRepository,
                muteDataRepository,
                auditService,
                sessionService,
                playerDisplayService,
                networkService,
                findService,
                topMenuCacheService,
                config
        );
    }

    private PlayerData createPlayer(int pid, String uuid, String name, int playtime, int rating, int points) {
        PlayerData data = new PlayerData(uuid, true);
        data.pid = pid;
        data.nickname = name;
        data.totalPlayTime = playtime;
        data.pvpRating = rating;
        data.hexedPoints = points;
        data.hexedRank = HexedRanks.HexedRank.newbie.ordinal();
        data.unlockedBadges = new HashSet<>();
        return data;
    }

    @Test
    @DisplayName("Valid merge consolidates playtime, ratings, badges, and reassigns matches")
    void merge_validAccounts_mergesAllStatsCorrectly() {
        PlayerData source = createPlayer(10, "uuid-source", "OldPlayer", 120, 1400, 15);
        source.unlockedBadges.add("badge-veteran");
        source.discordId = "discord-123";
        source.discordUsername = "old_user";
        source.discordLinkedAt = 1000L;

        PlayerData target = createPlayer(20, "uuid-target", "NewPlayer", 30, 1600, 10);
        target.unlockedBadges.add("badge-builder");

        when(findService.playerData("10")).thenReturn(source);
        when(findService.playerData("20")).thenReturn(target);
        when(gameDataRepository.reassignPlayerMatches("uuid-source", "uuid-target")).thenReturn(5L);

        AuditActor actor = AuditActor.builder().type(AuditActorType.SERVER_CONSOLE).nameSnapshot("Console").build();
        var request = new AccountMergeService.MergeRequest("10", "20", "Lost old phone", actor);

        var result = service.merge(request);

        assertThat(result.success()).isTrue();
        assertThat(result.gamesTransferred()).isEqualTo(5L);

        // Check target consolidation
        PlayerData targetAfter = result.targetAfter();
        assertThat(targetAfter.pid).isEqualTo(20);
        assertThat(targetAfter.totalPlayTime).isEqualTo(150); // 120 + 30
        assertThat(targetAfter.pvpRating).isEqualTo(1600); // max(1400, 1600)
        assertThat(targetAfter.hexedPoints).isEqualTo(25); // 15 + 10
        assertThat(targetAfter.unlockedBadges).containsExactlyInAnyOrder("badge-veteran", "badge-builder");
        assertThat(targetAfter.discordId).isEqualTo("discord-123");
        assertThat(targetAfter.discordUsername).isEqualTo("old_user");

        // Check source closed
        ArgumentCaptor<PlayerData> sourceCaptor = ArgumentCaptor.forClass(PlayerData.class);
        verify(playerDataRepository, atLeastOnce()).save(sourceCaptor.capture());

        PlayerData savedSource = sourceCaptor.getAllValues().stream()
                .filter(p -> p.pid == 10)
                .findFirst()
                .orElseThrow();
        assertThat(savedSource.uuid).isEqualTo("merged:uuid-source");
        assertThat(savedSource.totalPlayTime).isEqualTo(0);

        // Verify audit log
        ArgumentCaptor<AuditAppendCommand> auditCaptor = ArgumentCaptor.forClass(AuditAppendCommand.class);
        verify(auditService).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo(AuditAction.MERGE);
        assertThat(auditCaptor.getValue().target().pid).isEqualTo(20);

        // Verify cluster broadcast
        verify(networkService).post(any(PlayerDataCacheReloadCommandV1.class));
        verify(topMenuCacheService).invalidateAllAsync();
    }

    @Test
    @DisplayName("Merging an account into itself returns failure")
    void merge_sameAccount_fails() {
        PlayerData player = createPlayer(10, "uuid-same", "PlayerOne", 100, 1500, 5);
        when(findService.playerData("10")).thenReturn(player);

        var request = new AccountMergeService.MergeRequest("10", "10", "Self test", null);
        var result = service.merge(request);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Cannot merge account into itself");
        verifyNoInteractions(gameDataRepository);
    }

    @Test
    @DisplayName("Merging an already merged account returns failure")
    void merge_alreadyMergedAccount_fails() {
        PlayerData source = createPlayer(10, "merged:uuid-old", "MergedPlayer", 0, 1000, 0);
        PlayerData target = createPlayer(20, "uuid-target", "ActivePlayer", 50, 1200, 2);

        when(findService.playerData("10")).thenReturn(source);
        when(findService.playerData("20")).thenReturn(target);

        var request = new AccountMergeService.MergeRequest("10", "20", "Test", null);
        var result = service.merge(request);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("was already merged");
    }

    @Test
    @DisplayName("Transfer active ban and mute if source was punished")
    void merge_transfersActivePunishments() {
        PlayerData source = createPlayer(10, "uuid-source", "BannedSource", 50, 1000, 0);
        PlayerData target = createPlayer(20, "uuid-target", "CleanTarget", 10, 1000, 0);

        when(findService.playerData("10")).thenReturn(source);
        when(findService.playerData("20")).thenReturn(target);

        BanData activeBan = BanData.builder()
                .uuid("uuid-source")
                .adminName("Admin")
                .reason("Griefing")
                .expireDate(Instant.now().plusSeconds(3600))
                .build();
        when(banDataRepository.find("uuid-source", null)).thenReturn(activeBan);
        when(banDataRepository.find("uuid-target", null)).thenReturn(null);

        MuteData activeMute = MuteData.builder()
                .uuid("uuid-source")
                .adminName("Mod")
                .reason("Spam")
                .expireDate(Instant.now().plusSeconds(1800))
                .build();
        when(muteDataRepository.findByUuid("uuid-source")).thenReturn(activeMute);
        when(muteDataRepository.findByUuid("uuid-target")).thenReturn(null);

        var request = new AccountMergeService.MergeRequest("10", "20", "Merge banned user", null);
        var result = service.merge(request);

        assertThat(result.success()).isTrue();
        assertThat(result.banTransferred()).isTrue();
        assertThat(result.muteTransferred()).isTrue();

        verify(banDataRepository).save(argThat(b -> b.uuid.equals("uuid-target") && b.reason.contains("Griefing")));
        verify(muteDataRepository).save(argThat(m -> m.uuid.equals("uuid-target") && m.reason.contains("Spam")));
    }
}
