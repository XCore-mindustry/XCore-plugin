package org.xcore.plugin.service;

import com.mongodb.MongoCommandException;
import com.mongodb.ServerAddress;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.TransactionBody;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
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
        when(banDataRepository.save(any(BanData.class))).thenReturn(true);
        when(muteDataRepository.save(any(MuteData.class))).thenReturn(true);

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

    @Test
    @DisplayName("Transactional merge executes all mutations within MongoDB ClientSession boundary")
    void merge_withMongoClientTransaction_executesWithinSession() {
        MongoClient mongoClient = mock(MongoClient.class);
        ClientSession session = mock(ClientSession.class);
        when(mongoClient.startSession()).thenReturn(session);
        when(session.withTransaction(any())).thenAnswer(invocation -> {
            TransactionBody<?> body = invocation.getArgument(0);
            return body.execute();
        });

        when(playerDataRepository.save(eq(session), any(PlayerData.class))).thenReturn(true);
        when(banDataRepository.save(eq(session), any(BanData.class))).thenReturn(true);
        when(muteDataRepository.save(eq(session), any(MuteData.class))).thenReturn(true);
        when(gameDataRepository.reassignPlayerMatches(eq(session), anyString(), anyString())).thenReturn(3L);

        AccountMergeService txService = new AccountMergeService(
                mongoClient,
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

        PlayerData source = createPlayer(10, "uuid-source", "SourcePlayer", 100, 1500, 10);
        PlayerData target = createPlayer(20, "uuid-target", "TargetPlayer", 50, 1600, 20);

        when(findService.playerData("10")).thenReturn(source);
        when(findService.playerData("20")).thenReturn(target);

        var result = txService.merge(new AccountMergeService.MergeRequest("10", "20", "Admin merge", null));

        assertThat(result.success()).isTrue();
        assertThat(result.gamesTransferred()).isEqualTo(3L);

        // Verify session passed to repositories
        verify(playerDataRepository, times(2)).save(eq(session), any(PlayerData.class));
        verify(gameDataRepository).reassignPlayerMatches(eq(session), eq("uuid-source"), eq("uuid-target"));
        verify(auditService).append(eq(session), any(AuditAppendCommand.class));
        verify(session).close();
    }

    @Test
    @DisplayName("Transaction rollback on failure aborts and preserves original player data in memory")
    void merge_transactionFailure_rollsBackAndPreservesMemoryState() {
        MongoClient mongoClient = mock(MongoClient.class);
        ClientSession session = mock(ClientSession.class);
        when(mongoClient.startSession()).thenReturn(session);
        when(session.withTransaction(any())).thenAnswer(invocation -> {
            TransactionBody<?> body = invocation.getArgument(0);
            return body.execute();
        });

        // Simulate target save failure in transaction
        when(playerDataRepository.save(eq(session), argThat(p -> p.pid == 10))).thenReturn(true);
        when(playerDataRepository.save(eq(session), argThat(p -> p.pid == 20))).thenReturn(false);

        AccountMergeService txService = new AccountMergeService(
                mongoClient,
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

        PlayerData source = createPlayer(10, "uuid-source", "SourcePlayer", 100, 1500, 10);
        PlayerData target = createPlayer(20, "uuid-target", "TargetPlayer", 50, 1600, 20);

        when(findService.playerData("10")).thenReturn(source);
        when(findService.playerData("20")).thenReturn(target);

        var result = txService.merge(new AccountMergeService.MergeRequest("10", "20", "Admin merge", null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Transaction aborted");

        // Verify source in-memory data was NOT corrupted
        assertThat(source.uuid).isEqualTo("uuid-source");
        assertThat(source.totalPlayTime).isEqualTo(100);

        // Verify target in-memory data was NOT modified
        assertThat(target.totalPlayTime).isEqualTo(50);
        assertThat(target.hexedPoints).isEqualTo(20);

        // Verify external notifications were NOT sent
        verifyNoInteractions(networkService);
        verifyNoInteractions(topMenuCacheService);
    }

    @Test
    @DisplayName("Standalone MongoDB fallback gracefully merges when transactions are unsupported")
    void merge_standaloneFallback_mergesWithoutTransaction() {
        MongoClient mongoClient = mock(MongoClient.class);
        ClientSession session = mock(ClientSession.class);
        when(mongoClient.startSession()).thenReturn(session);

        BsonDocument response = new BsonDocument("ok", new BsonInt32(0))
                .append("code", new BsonInt32(20))
                .append("errmsg", new BsonString("Transaction numbers are only allowed on a replica set member or mongos"));
        MongoCommandException standaloneEx = new MongoCommandException(response, new ServerAddress("localhost", 27017));

        when(session.withTransaction(any())).thenThrow(standaloneEx);
        when(gameDataRepository.reassignPlayerMatches("uuid-source", "uuid-target")).thenReturn(2L);

        AccountMergeService txService = new AccountMergeService(
                mongoClient,
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

        PlayerData source = createPlayer(10, "uuid-source", "SourcePlayer", 60, 1300, 5);
        PlayerData target = createPlayer(20, "uuid-target", "TargetPlayer", 40, 1500, 10);

        when(findService.playerData("10")).thenReturn(source);
        when(findService.playerData("20")).thenReturn(target);

        var result = txService.merge(new AccountMergeService.MergeRequest("10", "20", "Standalone fallback merge", null));

        assertThat(result.success()).isTrue();
        assertThat(result.targetAfter().totalPlayTime).isEqualTo(100);
        verify(networkService).post(any(PlayerDataCacheReloadCommandV1.class));
    }

    @Test
    @DisplayName("mergeAccounts convenience method merges via console actor")
    void mergeAccounts_delegatesToMerge() {
        PlayerData source = createPlayer(10, "uuid-source", "SourcePlayer", 30, 1200, 0);
        PlayerData target = createPlayer(20, "uuid-target", "TargetPlayer", 30, 1200, 0);

        when(findService.playerData("uuid-source")).thenReturn(source);
        when(findService.playerData("uuid-target")).thenReturn(target);

        var result = service.mergeAccounts("uuid-source", "uuid-target");

        assertThat(result.success()).isTrue();
        assertThat(result.targetAfter().totalPlayTime).isEqualTo(60);
    }
}
