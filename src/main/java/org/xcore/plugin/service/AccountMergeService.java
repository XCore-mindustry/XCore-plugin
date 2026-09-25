package org.xcore.plugin.service;

import arc.Core;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;
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

    public CompletableFuture<MergeResult> mergeAsync(MergeRequest request) {
        return CompletableFuture.supplyAsync(() -> merge(request));
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

        // 1. Data consolidation
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

        // 2. Punishments transfer
        boolean banTransferred = false;
        boolean muteTransferred = false;

        BanData sourceBan = banDataRepository.find(source.uuid, null);
        if (sourceBan != null && !sourceBan.expired()) {
            BanData targetBan = banDataRepository.find(target.uuid, null);
            if (targetBan == null || targetBan.expired()) {
                BanData newBan = BanData.builder()
                        .uuid(target.uuid)
                        .ip(target.ip)
                        .name(target.nickname)
                        .adminName(sourceBan.adminName)
                        .adminDiscordId(sourceBan.adminDiscordId)
                        .reason("[Merged from #" + source.pid + "] " + sourceBan.reason)
                        .expireDate(sourceBan.expireDate)
                        .build();
                banDataRepository.save(newBan);
                banTransferred = true;
            }
        }

        MuteData sourceMute = muteDataRepository.findByUuid(source.uuid);
        if (sourceMute != null && !sourceMute.expired()) {
            MuteData targetMute = muteDataRepository.findByUuid(target.uuid);
            if (targetMute == null || targetMute.expired()) {
                MuteData newMute = MuteData.builder()
                        .uuid(target.uuid)
                        .name(target.nickname)
                        .adminName(sourceMute.adminName)
                        .adminDiscordId(sourceMute.adminDiscordId)
                        .reason("[Merged from #" + source.pid + "] " + sourceMute.reason)
                        .expireDate(sourceMute.expireDate)
                        .build();
                muteDataRepository.save(newMute);
                muteTransferred = true;
            }
        }

        // 3. Mark source account as merged and free original UUID from uniqueness constraint
        String oldSourceUuid = source.uuid;
        source.uuid = "merged:" + oldSourceUuid;
        source.totalPlayTime = 0;
        source.description = "Merged into PID #" + target.pid + " (" + target.nickname + ")";

        // 4. Persist to MongoDB
        boolean sourceSaved = playerDataRepository.save(source);
        boolean targetSaved = playerDataRepository.save(target);

        if (!sourceSaved || !targetSaved) {
            return MergeResult.failure("Failed to persist merged player data to database.");
        }

        // 5. Reassign matches in games_v2
        long gamesTransferred = gameDataRepository.reassignPlayerMatches(oldSourceUuid, target.uuid);

        // 6. Audit record
        Map<String, String> auditDetails = new HashMap<>();
        auditDetails.put("source_pid", String.valueOf(sourceBefore.pid));
        auditDetails.put("source_uuid", oldSourceUuid);
        auditDetails.put("source_nickname", sourceBefore.nickname);
        auditDetails.put("target_pid", String.valueOf(target.pid));
        auditDetails.put("target_uuid", target.uuid);
        auditDetails.put("target_nickname", target.nickname);
        auditDetails.put("playtime_added_minutes", String.valueOf(sourceBefore.totalPlayTime));
        auditDetails.put("hexed_points_added", String.valueOf(sourceBefore.hexedPoints));
        auditDetails.put("games_transferred", String.valueOf(gamesTransferred));
        auditDetails.put("ban_transferred", String.valueOf(banTransferred));
        auditDetails.put("mute_transferred", String.valueOf(muteTransferred));

        AuditActor actor = request.actor() != null ? request.actor() : AuditActor.builder()
                .type(AuditActorType.SERVER_CONSOLE)
                .nameSnapshot("Console")
                .id("console")
                .build();

        auditService.append(AuditAppendCommand.builder()
                .action(AuditAction.MERGE)
                .actor(actor)
                .target(AuditTarget.builder()
                        .uuid(target.uuid)
                        .pid(target.pid)
                        .nameSnapshot(target.nickname)
                        .build())
                .reason(request.reason() != null && !request.reason().isBlank() ? request.reason() : "Account merge")
                .details(AuditDetails.builder().extra(auditDetails).build())
                .build());

        // 7. Handle online sessions on this server
        handleOnlinePlayers(oldSourceUuid, target);

        // 8. Sync cluster via Redis
        if (networkService != null) {
            try {
                networkService.post(new PlayerDataCacheReloadCommandV1(config != null && config.server != null ? config.server.name : "server"));
            } catch (Exception e) {
                Log.warn("Failed to publish PlayerDataCacheReloadCommandV1: @", e.getMessage());
            }
        }

        // 9. Invalidate top menu cache
        if (topMenuCacheService != null) {
            topMenuCacheService.invalidateAllAsync();
        }

        PlayerData targetAfter = clonePlayerData(target);
        String successMsg = String.format("Successfully merged player #%d (%s) into #%d (%s). Transferred: %d min playtime, %d matches.",
                sourceBefore.pid, sourceBefore.nickname, target.pid, target.nickname, sourceBefore.totalPlayTime, gamesTransferred);

        return new MergeResult(true, successMsg, sourceBefore, targetBefore, targetAfter, gamesTransferred, banTransferred, muteTransferred);
    }

    private void handleOnlinePlayers(String oldSourceUuid, PlayerData targetData) {
        if (Core.app == null) return;

        // Kick source if currently connected
        Player sourcePlayer = Groups.player.find(p -> oldSourceUuid.equals(p.uuid()));
        if (sourcePlayer != null && sourcePlayer.con != null) {
            Core.app.post(() -> sourcePlayer.con.kick(
                    "Ваш аккаунт был объединен с аккаунтом #" + targetData.pid + ". Пожалуйста, перезайдите с нового аккаунта."));
        }

        // Refresh target if currently connected
        Player targetPlayer = Groups.player.find(p -> targetData.uuid.equals(p.uuid()));
        if (targetPlayer != null) {
            Session targetSession = sessionService.get(targetData.uuid);
            if (targetSession != null) {
                targetSession.data = targetData;
                if (playerDisplayService != null) {
                    Core.app.post(() -> playerDisplayService.refresh(targetSession));
                }
            }
        }
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
