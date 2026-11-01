package org.xcore.plugin.service;

import arc.Application;
import arc.Core;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.model.*;
import org.xcore.plugin.rating.ladder.LadderService;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionFactory;
import org.xcore.protocol.generated.messages.server.ServerMessages.PlayerDataCacheReloadCommandV1;
import org.xcore.testkit.core.DeterministicQueue;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
    private LadderService ladderService;
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
        ladderService = mock(LadderService.class);
        config = new TomlXcoreConfig();
        config.server.name = "test-server";

        when(playerDataRepository.saveMergedPlayer(isNull(), any(PlayerData.class), any(PlayerData.class))).thenReturn(true);
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
                ladderService,
                config
        );
    }

    private PlayerData createPlayer(int pid, String uuid, String name, int playtime, int rating, int points) {
        PlayerData data = new PlayerData(uuid, true);
        data.pid = pid;
        data.nickname = name;
        data.totalPlayTime = playtime;
        data.hexedPoints = points;
        data.hexedRank = HexedRanks.HexedRank.newbie.ordinal();
        data.unlockedBadges = new HashSet<>();
        when(playerDataRepository.findByUuid(uuid)).thenReturn(data);
        return data;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("Stale online bindings are refreshed in merge copies while cached progress survives")
    void mergeAsync_staleOnlineBindings_useStoredBindingsAndCachedProgress(boolean targetLinked) throws Exception {
        PlayerData source = createPlayer(10, "uuid-source", "Cached source", 120, 1400, 15);
        source.discordId = "stale-source";
        source.discordUsername = "stale-source-name";
        source.discordLinkedAt = 10L;
        source.unlockedBadges.add("source-badge");
        PlayerData target = createPlayer(20, "uuid-target", "Cached target", 30, 1600, 10);
        target.discordId = "stale-target";
        target.discordUsername = "stale-target-name";
        target.discordLinkedAt = 20L;
        target.unlockedBadges.add("target-badge");

        PlayerData storedSource = createPlayer(10, source.uuid, "Stored source", 1, 1000, 1);
        storedSource.discordId = "current-source";
        storedSource.discordUsername = "current-source-name";
        storedSource.discordLinkedAt = 100L;
        PlayerData storedTarget = createPlayer(20, target.uuid, "Stored target", 2, 1000, 2);
        if (targetLinked) {
            storedTarget.discordId = "current-target";
            storedTarget.discordUsername = "current-target-name";
            storedTarget.discordLinkedAt = 200L;
        }
        PlayerData expectedBinding = targetLinked ? storedTarget : storedSource;

        var onlineSessions = new SessionService(mock(SessionFactory.class), playerDataRepository);
        Session sourceSession = mock(Session.class);
        sourceSession.data = source;
        onlineSessions.update(sourceSession);
        Session targetSession = mock(Session.class);
        targetSession.data = target;
        onlineSessions.update(targetSession);
        var onlineService = new AccountMergeService(playerDataRepository, gameDataRepository,
                banDataRepository, muteDataRepository, auditService, onlineSessions, playerDisplayService,
                networkService, new FindService(onlineSessions, null), topMenuCacheService, ladderService, config);
        when(playerDataRepository.saveMergedPlayer(isNull(), any(PlayerData.class), any(PlayerData.class)))
                .thenAnswer(invocation -> {
                    PlayerData before = invocation.getArgument(1);
                    PlayerData current = before.pid == source.pid ? storedSource : storedTarget;
                    return Objects.equals(before.discordId, current.discordId)
                            && before.discordLinkedAt == current.discordLinkedAt;
                });

        Application previous = Core.app;
        var queue = new DeterministicQueue();
        var app = mock(Application.class);
        doAnswer(invocation -> {
            queue.post(invocation.getArgument(0));
            return null;
        }).when(app).post(any(Runnable.class));
        Core.app = app;
        try {
            var result = onlineService.mergeAccountsAsync(source.uuid, target.uuid).get(5, TimeUnit.SECONDS);

            assertThat(result.success()).isTrue();
            assertThat(result.sourceBefore().discordId).isEqualTo(storedSource.discordId);
            assertThat(result.sourceBefore().discordUsername).isEqualTo(storedSource.discordUsername);
            assertThat(result.sourceBefore().discordLinkedAt).isEqualTo(storedSource.discordLinkedAt);
            assertThat(result.targetBefore().discordId).isEqualTo(storedTarget.discordId);
            assertThat(result.targetBefore().discordUsername).isEqualTo(storedTarget.discordUsername);
            assertThat(result.targetBefore().discordLinkedAt).isEqualTo(storedTarget.discordLinkedAt);
            assertThat(result.sourceBefore().totalPlayTime).isEqualTo(120);
            assertThat(result.targetBefore().totalPlayTime).isEqualTo(30);
            assertThat(result.targetAfter().totalPlayTime).isEqualTo(150);
            assertThat(result.targetAfter().hexedPoints).isEqualTo(25);
            assertThat(result.targetAfter().nickname).isEqualTo("Cached target");
            assertThat(result.targetAfter().unlockedBadges).containsExactlyInAnyOrder("source-badge", "target-badge");
            assertThat(result.targetAfter().discordId).isEqualTo(expectedBinding.discordId);
            assertThat(result.targetAfter().discordUsername).isEqualTo(expectedBinding.discordUsername);
            assertThat(result.targetAfter().discordLinkedAt).isEqualTo(expectedBinding.discordLinkedAt);

            var writes = ArgumentCaptor.forClass(PlayerData.class);
            verify(playerDataRepository, times(2)).saveMergedPlayer(isNull(), any(PlayerData.class), writes.capture());
            assertThat(writes.getAllValues().getFirst().discordId).isEmpty();
            assertThat(writes.getAllValues().getLast().discordId).isEqualTo(expectedBinding.discordId);
            assertThat(writes.getAllValues().getLast().discordLinkedAt).isEqualTo(expectedBinding.discordLinkedAt);

            // The storage worker must not refresh or apply the merge to live session objects.
            assertThat(sourceSession.data).isSameAs(source);
            assertThat(source.uuid).isEqualTo("uuid-source");
            assertThat(source.discordId).isEqualTo("stale-source");
            assertThat(source.totalPlayTime).isEqualTo(120);
            assertThat(targetSession.data).isSameAs(target);
            assertThat(target.discordId).isEqualTo("stale-target");
            assertThat(target.totalPlayTime).isEqualTo(30);
            queue.runNext();
            assertThat(source.uuid).isEqualTo("merged:uuid-source");
            assertThat(target.discordId).isEqualTo(expectedBinding.discordId);
            assertThat(target.totalPlayTime).isEqualTo(150);
        } finally {
            Core.app = previous;
        }
    }

    @Test
    @DisplayName("A deleted stored player aborts before merge writes or cached-state changes")
    void merge_missingStoredPlayer_aborts() {
        PlayerData source = createPlayer(10, "uuid-source", "Source", 120, 1400, 15);
        PlayerData target = createPlayer(20, "uuid-target", "Target", 30, 1600, 10);
        when(findService.playerData("10")).thenReturn(source);
        when(findService.playerData("20")).thenReturn(target);
        when(playerDataRepository.findByUuid(source.uuid)).thenReturn(null);

        var result = service.merge(new AccountMergeService.MergeRequest("10", "20", "Merge", null));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("changed before merge");
        verify(playerDataRepository, never()).saveMergedPlayer(any(), any(), any());
        verifyNoInteractions(gameDataRepository, networkService);
        assertThat(source.totalPlayTime).isEqualTo(120);
        assertThat(target.totalPlayTime).isEqualTo(30);
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
        assertThat(targetAfter.hexedPoints).isEqualTo(25); // 15 + 10
        assertThat(targetAfter.unlockedBadges).containsExactlyInAnyOrder("badge-veteran", "badge-builder");
        assertThat(targetAfter.discordId).isEqualTo("discord-123");
        assertThat(targetAfter.discordUsername).isEqualTo("old_user");

        // Check source closed
        ArgumentCaptor<PlayerData> sourceCaptor = ArgumentCaptor.forClass(PlayerData.class);
        verify(playerDataRepository, atLeastOnce()).saveMergedPlayer(isNull(), any(PlayerData.class), sourceCaptor.capture());

        PlayerData savedSource = sourceCaptor.getAllValues().stream()
                .filter(p -> p.pid == 10)
                .findFirst()
                .orElseThrow();
        assertThat(savedSource.uuid).isEqualTo("merged:uuid-source");
        assertThat(savedSource.discordId).isEmpty();
        verify(playerDataRepository, never()).save(any(PlayerData.class));
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
    @DisplayName("Merge folds ladder standings into the target and refreshes ladder caches")
    void merge_validAccounts_mergesLadderStandings() {
        PlayerData source = createPlayer(10, "uuid-source", "OldPlayer", 120, 1400, 15);
        PlayerData target = createPlayer(20, "uuid-target", "NewPlayer", 30, 1600, 10);

        when(findService.playerData("10")).thenReturn(source);
        when(findService.playerData("20")).thenReturn(target);
        when(ladderService.mergePlayer(any(), eq("uuid-source"), eq("uuid-target"))).thenReturn(2);

        var result = service.merge(new AccountMergeService.MergeRequest("10", "20", "Lost old phone", null));

        assertThat(result.success()).isTrue();
        var order = inOrder(ladderService);
        order.verify(ladderService).mergePlayer(any(), eq("uuid-source"), eq("uuid-target"));
        order.verify(ladderService).reloadCaches();

        ArgumentCaptor<AuditAppendCommand> auditCaptor = ArgumentCaptor.forClass(AuditAppendCommand.class);
        verify(auditService).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue().details().extra).containsEntry("standings_merged", "2");
    }

    @Test
    @DisplayName("Merge leaves the target's credentials and admin status alone and revokes the source's")
    void merge_doesNotCarryCredentials() {
        PlayerData source = createPlayer(10, "uuid-source", "OldAdmin", 120, 1400, 15);
        source.admin = true;
        source.adminSource = "DISCORD_ROLE";
        source.password = "source-hash";
        source.addDeviceToken("source-token", System.currentTimeMillis() + 60_000L);

        PlayerData target = createPlayer(20, "uuid-target", "NewPlayer", 30, 1600, 10);
        target.admin = false;
        target.adminSource = "NONE";
        target.password = "target-hash";
        target.addDeviceToken("target-token", System.currentTimeMillis() + 60_000L);

        when(findService.playerData("10")).thenReturn(source);
        when(findService.playerData("20")).thenReturn(target);

        var result = service.merge(new AccountMergeService.MergeRequest("10", "20", "Lost old phone", null));

        assertThat(result.success()).isTrue();
        assertThat(target.admin).isFalse();
        assertThat(target.adminSource).isEqualTo("NONE");
        assertThat(target.password).isEqualTo("target-hash");
        assertThat(target.hasDeviceToken("target-token")).isTrue();
        assertThat(target.hasDeviceToken("source-token")).isFalse();

        assertThat(source.uuid).isEqualTo("merged:uuid-source");
        assertThat(source.admin).isFalse();
        assertThat(source.adminSource).isEqualTo("NONE");
        assertThat(source.password).isNull();
        assertThat(source.hasDeviceToken("source-token")).isFalse();
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

        when(playerDataRepository.saveMergedPlayer(eq(session), any(PlayerData.class), any(PlayerData.class))).thenReturn(true);
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
                ladderService,
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
        verify(playerDataRepository, times(2)).saveMergedPlayer(eq(session), any(PlayerData.class), any(PlayerData.class));
        verify(gameDataRepository).reassignPlayerMatches(eq(session), eq("uuid-source"), eq("uuid-target"));
        verify(auditService).append(eq(session), any(AuditAppendCommand.class));
        verify(session).close();
    }

    @Test
    @DisplayName("A binding generation changed after refresh aborts without changing cached player data")
    void merge_transactionFailure_rollsBackAndPreservesMemoryState() {
        MongoClient mongoClient = mock(MongoClient.class);
        ClientSession session = mock(ClientSession.class);
        PlayerData currentTarget = new PlayerData("uuid-target", true);
        currentTarget.discordId = "current-target";
        currentTarget.discordLinkedAt = 50L;
        when(mongoClient.startSession()).thenReturn(session);
        when(session.withTransaction(any())).thenAnswer(invocation -> {
            // The merge already read generation 50; a concurrent relink advances it before the CAS.
            currentTarget.discordLinkedAt = 51L;
            TransactionBody<?> body = invocation.getArgument(0);
            return body.execute();
        });

        when(playerDataRepository.saveMergedPlayer(eq(session), argThat(b -> b != null && "uuid-source".equals(b.uuid)), argThat(p -> p.pid == 10))).thenReturn(true);
        when(playerDataRepository.saveMergedPlayer(eq(session), argThat(b -> b != null && "uuid-target".equals(b.uuid)), argThat(p -> p.pid == 20)))
                .thenAnswer(invocation -> invocation.<PlayerData>getArgument(1).discordLinkedAt == currentTarget.discordLinkedAt);

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
                ladderService,
                config
        );

        PlayerData source = createPlayer(10, "uuid-source", "SourcePlayer", 100, 1500, 10);
        PlayerData target = createPlayer(20, "uuid-target", "TargetPlayer", 50, 1600, 20);
        target.discordId = "stale-target";
        target.discordLinkedAt = 10L;
        when(playerDataRepository.findByUuid(target.uuid)).thenReturn(currentTarget);

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
        assertThat(target.discordId).isEqualTo("stale-target");
        assertThat(target.discordLinkedAt).isEqualTo(10L);
        assertThat(currentTarget.discordLinkedAt).isEqualTo(51L);
        var beforeWrite = ArgumentCaptor.forClass(PlayerData.class);
        verify(playerDataRepository).saveMergedPlayer(eq(session), beforeWrite.capture(), argThat(p -> p.pid == 20));
        assertThat(beforeWrite.getValue().discordId).isEqualTo("current-target");
        assertThat(beforeWrite.getValue().discordLinkedAt).isEqualTo(50L);

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
                ladderService,
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
