package org.xcore.plugin.service;

import arc.Core;
import arc.util.Log;
import com.mongodb.MongoClientException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.database.repository.GameDataRepository;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.model.*;
import org.xcore.plugin.service.moderation.AuditService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.protocol.generated.messages.server.ServerMessages.PlayerDataCacheReloadCommandV1;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Administrative utility service for merging a source player account into a target player account.
 * Consolidates playtime, ratings, points, badges, linkage, device tokens, and match history.
 * Performs atomic multi-entity merging via MongoDB ClientSession transactions when available,
 * falling back cleanly to non-transactional mode for standalone MongoDB instances.
 * Intended ONLY for Server Console and Discord Bot administration.
 */
@Singleton
public class AccountMergeService {

    public record MergeRequest(
            String sourceIdentifier,
            String targetIdentifier,
            String reason,
            AuditActor actor
    ) {}

    public record MergeResult(
            boolean success,
            String message,
            PlayerData sourceBefore,
            PlayerData targetBefore,
            PlayerData targetAfter,
            long gamesTransferred,
            boolean banTransferred,
            boolean muteTransferred
    ) {
        public static MergeResult failure(String message) {
            return new MergeResult(false, message, null, null, null, 0, false, false);
        }
    }

    private record MergeExecutionOutcome(
            boolean success,
            String errorMessage,
            long gamesTransferred,
            boolean banTransferred,
            boolean muteTransferred
    ) {
        public static MergeExecutionOutcome failure(String message) {
            return new MergeExecutionOutcome(false, message, 0, false, false);
        }
    }

    private final @Nullable MongoClient mongoClient;
    private final PlayerDataRepository playerDataRepository;
    private final GameDataRepository gameDataRepository;
    private final BanDataRepository banDataRepository;
    private final MuteDataRepository muteDataRepository;
    private final AuditService auditService;
    private final SessionService sessionService;
    private final PlayerDisplayService playerDisplayService;
    private final NetworkService networkService;
    private final FindService findService;
    private final TopMenuCacheService topMenuCacheService;
    private final TomlXcoreConfig config;

    @Inject
    public AccountMergeService(
            @Nullable MongoClient mongoClient,
            PlayerDataRepository playerDataRepository,
            GameDataRepository gameDataRepository,
            BanDataRepository banDataRepository,
            MuteDataRepository muteDataRepository,
            AuditService auditService,
            SessionService sessionService,
            PlayerDisplayService playerDisplayService,
            NetworkService networkService,
            FindService findService,
            TopMenuCacheService topMenuCacheService,
            TomlXcoreConfig config
    ) {
        this.mongoClient = mongoClient;
        this.playerDataRepository = playerDataRepository;
        this.gameDataRepository = gameDataRepository;
        this.banDataRepository = banDataRepository;
        this.muteDataRepository = muteDataRepository;
        this.auditService = auditService;
        this.sessionService = sessionService;
        this.playerDisplayService = playerDisplayService;
        this.networkService = networkService;
        this.findService = findService;
        this.topMenuCacheService = topMenuCacheService;
        this.config = config;
    }

    public AccountMergeService(
            PlayerDataRepository playerDataRepository,
            GameDataRepository gameDataRepository,
            BanDataRepository banDataRepository,
            MuteDataRepository muteDataRepository,
            AuditService auditService,
            SessionService sessionService,
            PlayerDisplayService playerDisplayService,
            NetworkService networkService,
            FindService findService,
            TopMenuCacheService topMenuCacheService,
            TomlXcoreConfig config
    ) {
        this(null, playerDataRepository, gameDataRepository, banDataRepository, muteDataRepository, auditService, sessionService, playerDisplayService, networkService, findService, topMenuCacheService, config);
    }

    public CompletableFuture<MergeResult> mergeAsync(MergeRequest request) {
        return CompletableFuture.supplyAsync(() -> merge(request));
    }

    public CompletableFuture<MergeResult> mergeAccountsAsync(String sourceUuid, String targetUuid) {
        return CompletableFuture.supplyAsync(() -> mergeAccounts(sourceUuid, targetUuid));
    }

    public MergeResult mergeAccounts(String sourceUuid, String targetUuid) {
        AuditActor actor = AuditActor.builder()
                .type(AuditActorType.SERVER_CONSOLE)
                .nameSnapshot("Console")
                .id("console")
                .build();
        return merge(new MergeRequest(sourceUuid, targetUuid, "Account merge", actor));
    }

    public MergeResult merge(MergeRequest request) {
        if (request == null) {
            return MergeResult.failure("Merge request cannot be null");
        }
        if (request.sourceIdentifier() == null || request.sourceIdentifier().isBlank()) {
            return MergeResult.failure("Source identifier cannot be empty");
        }
        if (request.targetIdentifier() == null || request.targetIdentifier().isBlank()) {
            return MergeResult.failure("Target identifier cannot be empty");
        }

        PlayerData source = findService.playerData(request.sourceIdentifier().trim());
        if (source == null) {
            return MergeResult.failure("Source player not found: " + request.sourceIdentifier());
        }

        PlayerData target = findService.playerData(request.targetIdentifier().trim());
        if (target == null) {
            return MergeResult.failure("Target player not found: " + request.targetIdentifier());
        }

        if (source.pid == target.pid || Objects.equals(source.uuid, target.uuid)) {
            return MergeResult.failure("Cannot merge account into itself (PID #" + source.pid + ")");
        }

        if (source.uuid != null && source.uuid.startsWith("merged:")) {
            return MergeResult.failure("Source player #" + source.pid + " was already merged into another account.");
        }

        // Snapshots before merge
        PlayerData sourceBefore = clonePlayerData(source);
        PlayerData targetBefore = clonePlayerData(target);

        // Working copies for atomic persistence
        PlayerData sourceWorking = clonePlayerData(source);
        PlayerData targetWorking = clonePlayerData(target);

        // 1. Data consolidation
        consolidateData(sourceWorking, targetWorking);

        String oldSourceUuid = sourceBefore.uuid;
        sourceWorking.uuid = "merged:" + oldSourceUuid;
        sourceWorking.totalPlayTime = 0;
        sourceWorking.description = "Merged into PID #" + targetWorking.pid + " (" + targetWorking.nickname + ")";

        AuditActor actor = request.actor() != null ? request.actor() : AuditActor.builder()
                .type(AuditActorType.SERVER_CONSOLE)
                .nameSnapshot("Console")
                .id("console")
                .build();

        // 2. Execute atomic transactional merge with MongoDB ClientSession
        MergeExecutionOutcome outcome = executeAtomicMerge(oldSourceUuid, sourceBefore, sourceWorking, targetWorking, request, actor);
        if (!outcome.success()) {
            return MergeResult.failure(outcome.errorMessage());
        }

        // 3. Reflect successful merge in in-memory objects (e.g. session cache references)
        applyMergedState(source, sourceWorking);
        applyMergedState(target, targetWorking);

        // 4. Handle online sessions on this server
        handleOnlinePlayers(oldSourceUuid, targetWorking);

        // 5. Sync cluster via Redis
        if (networkService != null) {
            try {
                networkService.post(new PlayerDataCacheReloadCommandV1(config != null && config.server != null ? config.server.name : "server"));
            } catch (Exception e) {
                Log.warn("Failed to publish PlayerDataCacheReloadCommandV1: @", e.getMessage());
            }
        }

        // 6. Invalidate top menu cache
        if (topMenuCacheService != null) {
            topMenuCacheService.invalidateAllAsync();
        }

        PlayerData targetAfter = clonePlayerData(targetWorking);
        String successMsg = String.format("Successfully merged player #%d (%s) into #%d (%s). Transferred: %d min playtime, %d matches.",
                sourceBefore.pid, sourceBefore.nickname, targetWorking.pid, targetWorking.nickname, sourceBefore.totalPlayTime, outcome.gamesTransferred());

        return new MergeResult(true, successMsg, sourceBefore, targetBefore, targetAfter, outcome.gamesTransferred(), outcome.banTransferred(), outcome.muteTransferred());
    }

    private void consolidateData(PlayerData source, PlayerData target) {
        target.totalPlayTime += source.totalPlayTime;
        target.pvpRating = Math.max(target.pvpRating, source.pvpRating);
        target.hexedPoints += source.hexedPoints;
        target.hexedRank = computeHexedRank(target.hexedPoints).ordinal();

        if (source.unlockedBadges != null) {
            if (target.unlockedBadges == null) target.unlockedBadges = new HashSet<>();
            target.unlockedBadges.addAll(source.unlockedBadges);
        }

        if ((target.activeBadge == null || target.activeBadge.isBlank())
                && source.activeBadge != null && !source.activeBadge.isBlank()) {
            target.activeBadge = source.activeBadge;
        }

        if ((target.customNickname == null || target.customNickname.isBlank())
                && source.customNickname != null && !source.customNickname.isBlank()) {
            target.customNickname = source.customNickname;
        }

        if ((target.description == null || target.description.isBlank())
                && source.description != null && !source.description.isBlank()) {
            target.description = source.description;
        }

        // Discord linkage transfer if target not linked
        if ((target.discordId == null || target.discordId.isBlank())
                && source.discordId != null && !source.discordId.isBlank()) {
            target.discordId = source.discordId;
            target.discordUsername = source.discordUsername;
            target.discordLinkedAt = source.discordLinkedAt;
        }

        if (source.deviceTokens != null) {
            if (target.deviceTokens == null) target.deviceTokens = new HashMap<>();
            target.deviceTokens.putAll(source.deviceTokens);
        }
        if (source.deviceTokenHashes != null) {
            if (target.deviceTokenHashes == null) target.deviceTokenHashes = new HashSet<>();
            target.deviceTokenHashes.addAll(source.deviceTokenHashes);
        }
        if (source.mapVotes != null) {
            if (target.mapVotes == null) target.mapVotes = new HashMap<>();
            target.mapVotes.putAll(source.mapVotes);
        }
        if (source.eventVotes != null) {
            if (target.eventVotes == null) target.eventVotes = new HashMap<>();
            target.eventVotes.putAll(source.eventVotes);
        }
        if (source.blockedPrivateUuids != null) {
            if (target.blockedPrivateUuids == null) target.blockedPrivateUuids = new HashSet<>();
            target.blockedPrivateUuids.addAll(source.blockedPrivateUuids);
        }

        target.admin = target.admin || source.admin;
        if (target.admin && "NONE".equals(target.adminSource) && !"NONE".equals(source.adminSource)) {
            target.adminSource = source.adminSource;
        }
    }

    private MergeExecutionOutcome executeAtomicMerge(
            String oldSourceUuid,
            PlayerData sourceBefore,
            PlayerData sourceWorking,
            PlayerData targetWorking,
            MergeRequest request,
            AuditActor actor
    ) {
        if (mongoClient != null) {
            try (ClientSession session = mongoClient.startSession()) {
                try {
                    return session.withTransaction(() -> executeMergeInSession(session, oldSourceUuid, sourceBefore, sourceWorking, targetWorking, request, actor));
                } catch (MongoException ex) {
                    if (isTransactionUnsupportedException(ex)) {
                        Log.warn("[AccountMerge] MongoDB transactions unsupported (standalone mode?); falling back to non-transactional merge: @", ex.getMessage());
                        return executeMergeInSession(null, oldSourceUuid, sourceBefore, sourceWorking, targetWorking, request, actor);
                    }
                    PLog.errTag("AccountMerge", "Failed to merge accounts @ -> @. Transaction aborted: @", oldSourceUuid, targetWorking.uuid, ex.getMessage(), ex);
                    return MergeExecutionOutcome.failure("Transaction aborted: " + ex.getMessage());
                } catch (Exception ex) {
                    PLog.errTag("AccountMerge", "Failed to merge accounts @ -> @. Transaction aborted: @", oldSourceUuid, targetWorking.uuid, ex.getMessage(), ex);
                    return MergeExecutionOutcome.failure("Transaction aborted: " + ex.getMessage());
                }
            } catch (MongoException ex) {
                if (isTransactionUnsupportedException(ex)) {
                    Log.warn("[AccountMerge] MongoDB sessions unsupported; falling back to non-transactional merge: @", ex.getMessage());
                    return executeMergeInSession(null, oldSourceUuid, sourceBefore, sourceWorking, targetWorking, request, actor);
                }
                PLog.errTag("AccountMerge", "Failed to start MongoDB session for @ -> @: @", oldSourceUuid, targetWorking.uuid, ex.getMessage(), ex);
                return MergeExecutionOutcome.failure("Failed to start MongoDB session: " + ex.getMessage());
            }
        }

        return executeMergeInSession(null, oldSourceUuid, sourceBefore, sourceWorking, targetWorking, request, actor);
    }

    private MergeExecutionOutcome executeMergeInSession(
            @Nullable ClientSession session,
            String oldSourceUuid,
            PlayerData sourceBefore,
            PlayerData sourceWorking,
            PlayerData targetWorking,
            MergeRequest request,
            AuditActor actor
    ) {
        // 1. Transfer active punishments
        boolean banTransferred = false;
        boolean muteTransferred = false;

        BanData sourceBan = (session != null)
                ? banDataRepository.find(session, oldSourceUuid, null)
                : banDataRepository.find(oldSourceUuid, null);

        if (sourceBan != null && !sourceBan.expired()) {
            BanData targetBan = (session != null)
                    ? banDataRepository.find(session, targetWorking.uuid, null)
                    : banDataRepository.find(targetWorking.uuid, null);

            if (targetBan == null || targetBan.expired()) {
                BanData newBan = BanData.builder()
                        .uuid(targetWorking.uuid)
                        .ip(targetWorking.ip)
                        .name(targetWorking.nickname)
                        .adminName(sourceBan.adminName)
                        .adminDiscordId(sourceBan.adminDiscordId)
                        .reason("[Merged from #" + sourceBefore.pid + "] " + sourceBan.reason)
                        .expireDate(sourceBan.expireDate)
                        .build();

                boolean saved = (session != null)
                        ? banDataRepository.save(session, newBan)
                        : banDataRepository.save(newBan);
                if (!saved) {
                    throw new IllegalStateException("Failed to persist transferred ban for target account");
                }
                banTransferred = true;
            }
        }

        MuteData sourceMute = (session != null)
                ? muteDataRepository.findByUuid(session, oldSourceUuid)
                : muteDataRepository.findByUuid(oldSourceUuid);

        if (sourceMute != null && !sourceMute.expired()) {
            MuteData targetMute = (session != null)
                    ? muteDataRepository.findByUuid(session, targetWorking.uuid)
                    : muteDataRepository.findByUuid(targetWorking.uuid);

            if (targetMute == null || targetMute.expired()) {
                MuteData newMute = MuteData.builder()
                        .uuid(targetWorking.uuid)
                        .name(targetWorking.nickname)
                        .adminName(sourceMute.adminName)
                        .adminDiscordId(sourceMute.adminDiscordId)
                        .reason("[Merged from #" + sourceBefore.pid + "] " + sourceMute.reason)
                        .expireDate(sourceMute.expireDate)
                        .build();

                boolean saved = (session != null)
                        ? muteDataRepository.save(session, newMute)
                        : muteDataRepository.save(newMute);
                if (!saved) {
                    throw new IllegalStateException("Failed to persist transferred mute for target account");
                }
                muteTransferred = true;
            }
        }

        // 2. Persist PlayerData
        boolean sourceSaved = (session != null)
                ? playerDataRepository.save(session, sourceWorking)
                : playerDataRepository.save(sourceWorking);

        boolean targetSaved = (session != null)
                ? playerDataRepository.save(session, targetWorking)
                : playerDataRepository.save(targetWorking);

        if (!sourceSaved || !targetSaved) {
            throw new IllegalStateException("Failed to persist merged player data to database.");
        }

        // 3. Reassign matches in games_v2
        long gamesTransferred = (session != null)
                ? gameDataRepository.reassignPlayerMatches(session, oldSourceUuid, targetWorking.uuid)
                : gameDataRepository.reassignPlayerMatches(oldSourceUuid, targetWorking.uuid);

        // 4. Record AuditRecord within same transaction boundary
        Map<String, String> auditDetails = new HashMap<>();
        auditDetails.put("source_pid", String.valueOf(sourceBefore.pid));
        auditDetails.put("source_uuid", oldSourceUuid);
        auditDetails.put("source_nickname", sourceBefore.nickname);
        auditDetails.put("target_pid", String.valueOf(targetWorking.pid));
        auditDetails.put("target_uuid", targetWorking.uuid);
        auditDetails.put("target_nickname", targetWorking.nickname);
        auditDetails.put("playtime_added_minutes", String.valueOf(sourceBefore.totalPlayTime));
        auditDetails.put("hexed_points_added", String.valueOf(sourceBefore.hexedPoints));
        auditDetails.put("games_transferred", String.valueOf(gamesTransferred));
        auditDetails.put("ban_transferred", String.valueOf(banTransferred));
        auditDetails.put("mute_transferred", String.valueOf(muteTransferred));
        auditDetails.put("atomic_transaction", String.valueOf(session != null));

        AuditAppendCommand appendCommand = AuditAppendCommand.builder()
                .action(AuditAction.MERGE)
                .actor(actor)
                .target(AuditTarget.builder()
                        .uuid(targetWorking.uuid)
                        .pid(targetWorking.pid)
                        .nameSnapshot(targetWorking.nickname)
                        .build())
                .reason(request.reason() != null && !request.reason().isBlank() ? request.reason() : "Account merge")
                .details(AuditDetails.builder().extra(auditDetails).build())
                .build();

        AuditAppendResult auditResult = (session != null)
                ? auditService.append(session, appendCommand)
                : auditService.append(appendCommand);

        if (auditResult != null && !auditResult.isSuccess()) {
            throw new IllegalStateException("Failed to append audit record: " + auditResult.getMessage().orElse("unknown error"));
        }

        return new MergeExecutionOutcome(true, null, gamesTransferred, banTransferred, muteTransferred);
    }

    private boolean isTransactionUnsupportedException(MongoException ex) {
        if (ex instanceof MongoCommandException cmdEx) {
            if (cmdEx.getErrorCode() == 20) {
                return true;
            }
        }
        String msg = ex.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("standalone")
                || lower.contains("transaction numbers are only allowed on a replica set member or mongos")
                || lower.contains("sessions are not supported")
                || lower.contains("replica set")
                || ex instanceof MongoClientException;
    }

    private void applyMergedState(PlayerData dest, PlayerData src) {
        if (dest == null || src == null || dest == src) return;
        dest.uuid = src.uuid;
        dest.pid = src.pid;
        dest.totalPlayTime = src.totalPlayTime;
        dest.pvpRating = src.pvpRating;
        dest.hexedPoints = src.hexedPoints;
        dest.hexedRank = src.hexedRank;
        dest.unlockedBadges = src.unlockedBadges != null ? new HashSet<>(src.unlockedBadges) : new HashSet<>();
        dest.activeBadge = src.activeBadge;
        dest.customNickname = src.customNickname;
        dest.description = src.description;
        dest.discordId = src.discordId;
        dest.discordUsername = src.discordUsername;
        dest.discordLinkedAt = src.discordLinkedAt;
        dest.deviceTokens = src.deviceTokens != null ? new HashMap<>(src.deviceTokens) : new HashMap<>();
        dest.deviceTokenHashes = src.deviceTokenHashes != null ? new HashSet<>(src.deviceTokenHashes) : new HashSet<>();
        dest.mapVotes = src.mapVotes != null ? new HashMap<>(src.mapVotes) : new HashMap<>();
        dest.eventVotes = src.eventVotes != null ? new HashMap<>(src.eventVotes) : new HashMap<>();
        dest.blockedPrivateUuids = src.blockedPrivateUuids != null ? new HashSet<>(src.blockedPrivateUuids) : new HashSet<>();
        dest.admin = src.admin;
        dest.adminSource = src.adminSource;
    }

    private void handleOnlinePlayers(String oldSourceUuid, PlayerData targetData) {
        // Read once: Core.app is cleared during shutdown, so re-reading it after the null
        // check could hand the task to a dead application.
        var app = Core.app;
        if (app == null) return;

        // This is reached from mergeAsync, which runs on a plain CompletableFuture pool.
        // Groups.player, the session cache and session.data all belong to the game thread,
        // so the whole method is marshalled rather than just the two calls that already
        // were - the Groups lookups and the session.data swap above used to happen on the
        // merge thread while the tick loop was reading them.
        app.post(() -> {
            // Kick source if currently connected
            Player sourcePlayer = Groups.player.find(p -> oldSourceUuid.equals(p.uuid()));
            if (sourcePlayer != null && sourcePlayer.con != null) {
                sourcePlayer.con.kick(
                        "Ваш аккаунт был объединен с аккаунтом #" + targetData.pid + ". Пожалуйста, перезайдите с нового аккаунта.");
            }

            // Refresh target if currently connected
            Player targetPlayer = Groups.player.find(p -> targetData.uuid.equals(p.uuid()));
            if (targetPlayer != null) {
                Session targetSession = sessionService.get(targetData.uuid);
                if (targetSession != null) {
                    targetSession.data = targetData;
                    if (playerDisplayService != null) {
                        playerDisplayService.refresh(targetSession);
                    }
                }
            }
        });
    }

    private HexedRanks.HexedRank computeHexedRank(int points) {
        var current = HexedRanks.HexedRank.newbie;
        while (current.hasNext() && current.checkNext(points)) {
            current = current.next;
        }
        return current;
    }

    private PlayerData clonePlayerData(PlayerData original) {
        if (original == null) return null;
        PlayerData copy = new PlayerData(original.uuid, original.exists);
        copy.id = original.id;
        copy.pid = original.pid;
        copy.ip = original.ip;
        copy.nickname = original.nickname;
        copy.customNickname = original.customNickname;
        copy.description = original.description;
        copy.password = original.password;
        copy.language = original.language;
        copy.translatorLanguage = original.translatorLanguage;
        copy.globalChatVisible = original.globalChatVisible;
        copy.discordRelayVisible = original.discordRelayVisible;
        copy.pvpRating = original.pvpRating;
        copy.hexedRank = original.hexedRank;
        copy.hexedPoints = original.hexedPoints;
        copy.totalPlayTime = original.totalPlayTime;
        copy.admin = original.admin;
        copy.adminSource = original.adminSource;
        copy.leaderboard = original.leaderboard;
        copy.discordId = original.discordId;
        copy.discordUsername = original.discordUsername;
        copy.discordLinkedAt = original.discordLinkedAt;
        copy.activeBadge = original.activeBadge;
        copy.badgeSymbolColorMode = original.badgeSymbolColorMode;
        copy.unlockedBadges = original.unlockedBadges != null ? new HashSet<>(original.unlockedBadges) : new HashSet<>();
        copy.deviceTokens = original.deviceTokens != null ? new HashMap<>(original.deviceTokens) : new HashMap<>();
        copy.deviceTokenHashes = original.deviceTokenHashes != null ? new HashSet<>(original.deviceTokenHashes) : new HashSet<>();
        copy.mapVotes = original.mapVotes != null ? new HashMap<>(original.mapVotes) : new HashMap<>();
        copy.eventVotes = original.eventVotes != null ? new HashMap<>(original.eventVotes) : new HashMap<>();
        copy.blockedPrivateUuids = original.blockedPrivateUuids != null ? new HashSet<>(original.blockedPrivateUuids) : new HashSet<>();
        return copy;
    }
}
