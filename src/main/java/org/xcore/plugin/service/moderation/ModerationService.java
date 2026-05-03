package org.xcore.plugin.service.moderation;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.BanDataRepository;
import org.xcore.plugin.database.repository.MuteDataRepository;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.AuditAction;
import org.xcore.plugin.model.AuditActor;
import org.xcore.plugin.model.AuditActorType;
import org.xcore.plugin.model.AuditAppendCommand;
import org.xcore.plugin.model.AuditDetails;
import org.xcore.plugin.model.AuditOrigin;
import org.xcore.plugin.model.AuditOriginChannel;
import org.xcore.plugin.model.AuditRecord;
import org.xcore.plugin.model.AuditTarget;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.MuteData;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.network.ModerationProtocolMapper;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.service.TimeService;
import org.xcore.protocol.generated.messages.moderation.ModerationMessages.ModerationPardonCommandV1;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static mindustry.Vars.netServer;

/**
 * Centralized moderation service handling ban, unban, mute, unmute operations.
 * Eliminates code duplication across client, server, and Discord controllers.
 */
@Singleton
public class ModerationService {
    private static final String DEFAULT_REASON = "Not Specified";
    private static final String UNKNOWN_PLAYER_NAME = "Unknown";
    private static final String EMPTY_DISCORD_ID = "";
    private static final String PLAYER_NOT_FOUND_MESSAGE = "Player not found";
    private static final String MISSING_IDENTIFIER_MESSAGE = "Either UUID or IP must be provided";
    private static final String BAN_SAVE_FAILED_MESSAGE = "Failed to save ban";
    private static final String BAN_DELETE_FAILED_MESSAGE = "Failed to delete ban";
    private static final String MUTE_SAVE_FAILED_MESSAGE = "Failed to save mute";
    private static final String MUTE_DELETE_FAILED_MESSAGE = "Failed to delete mute";

    private final PlayerDataRepository playerDataRepository;
    private final BanDataRepository banDataRepository;
    private final MuteDataRepository muteDataRepository;
    private final SessionService sessionService;
    private final NetworkService network;
    private final FindService find;
    private final TimeService time;
    private final AuditService auditService;
    private final Config config;

    @Inject
    public ModerationService(PlayerDataRepository playerDataRepository,
                             BanDataRepository banDataRepository,
                             MuteDataRepository muteDataRepository,
                             SessionService sessionService,
                             NetworkService network,
                             FindService find,
                             TimeService timeService,
                             AuditService auditService,
                             Config config) {
        this.playerDataRepository = playerDataRepository;
        this.banDataRepository = banDataRepository;
        this.muteDataRepository = muteDataRepository;
        this.sessionService = sessionService;
        this.network = network;
        this.find = find;
        this.time = timeService;
        this.auditService = auditService;
        this.config = config;
    }

    /**
     * Ban a player by their ID.
     *
     * @param id          Player ID
     * @param adminName   Name of the admin performing the ban
     * @param reason      Ban reason
     * @param duration    Ban duration period
     * @param kickOnline  Whether to kick the player if online
     * @return Result containing BanData if successful
     */
    public ModerationResult<BanData> banById(int id, String adminName, String adminDiscordId, String reason, Duration duration, boolean kickOnline) {
        var target = playerDataRepository.findByPid(id);
        if (target == null) {
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        Instant unbanDate = toExpireDate(duration);
        var info = netServer.admins.getInfoOptional(target.uuid);
        String ip = (info != null) ? info.lastIP : null;

        BanData ban = BanData.builder()
                .name(target.nickname)
                .uuid(target.uuid)
                .ip(ip)
                .adminName(adminName)
                .adminDiscordId(resolveAdminDiscordId(adminDiscordId))
                .reason(resolveReason(reason))
                .expireDate(unbanDate)
                .build();

        if (!banDataRepository.save(ban)) {
            return ModerationResult.failure(BAN_SAVE_FAILED_MESSAGE);
        }

        AuditRecord audit = appendAudit(
                AuditAction.BAN,
                auditTarget(target.uuid, target.pid, target.nickname, ip),
                legacyActor(adminName, adminDiscordId),
                legacyOrigin(adminName),
                ban.reason,
                auditDetails(duration, unbanDate),
                null
        );

        postBanEvents(ban, audit);
        postAuditEvent(audit);

        if (kickOnline) {
            network.post(ModerationProtocolMapper.toKickBannedCommand(
                    target.uuid,
                    target.pid,
                    target.nickname,
                    ip,
                    config.server,
                    commandOccurredAt(audit)
            ));
        }

        return ModerationResult.success("Player '" + target.nickname + "' banned successfully", ban);
    }

    /**
     * Unban a player by their ID.
     *
     * @param id Player ID
     * @return Result containing PlayerData if successful
     */
    public ModerationResult<PlayerData> unbanById(int id, String adminName, String adminDiscordId) {
        var target = playerDataRepository.findByPid(id);
        if (target == null) {
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        if (!banDataRepository.delete(target.uuid, null)) {
            return ModerationResult.failure(BAN_DELETE_FAILED_MESSAGE);
        }

        AuditRecord audit = appendAudit(
                AuditAction.UNBAN,
                auditTarget(target.uuid, target.pid, target.nickname, null),
                legacyActor(adminName, adminDiscordId),
                legacyOrigin(adminName),
                DEFAULT_REASON,
                new AuditDetails(),
                null
        );

        postAuditEvent(audit);
        network.post(toPardonCommand(target.uuid, target.pid, target.nickname, null, audit));

        return ModerationResult.success("Player '" + target.nickname + "' unbanned successfully", target);
    }

    /**
     * Mute a player by their ID.
     *
     * @param id        Player ID
     * @param adminName Name of the admin performing the mute
     * @param reason    Mute reason
     * @param duration  Mute duration period
     * @return Result containing MuteData if successful
     */
    public ModerationResult<MuteData> muteById(int id, String adminName, String adminDiscordId, String reason, Duration duration) {
        var target = sessionService.getOrLoadFromDb(id);
        if (target == null) {
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        Instant expireDate = toExpireDate(duration);

        MuteData mute = MuteData.builder()
                .uuid(target.uuid)
                .name(target.nickname)
                .adminName(adminName)
                .adminDiscordId(resolveAdminDiscordId(adminDiscordId))
                .reason(resolveReason(reason))
                .expireDate(expireDate)
                .build();

        if (!muteDataRepository.save(mute)) {
            return ModerationResult.failure(MUTE_SAVE_FAILED_MESSAGE);
        }

        AuditRecord audit = appendAudit(
                AuditAction.MUTE,
                auditTarget(target.uuid, target.pid, target.nickname, null),
                legacyActor(adminName, adminDiscordId),
                legacyOrigin(adminName),
                mute.reason,
                auditDetails(duration, expireDate),
                null
        );

        network.post(ModerationProtocolMapper.toMuteCreated(mute, config.server, eventOccurredAt(audit)));
        postAuditEvent(audit);

        return ModerationResult.success("Player '" + target.nickname + "' muted successfully", mute);
    }

    /**
     * Unmute a player by their ID.
     *
     * @param id Player ID
     * @return Result containing PlayerData if successful
     */
    public ModerationResult<PlayerData> unmuteById(int id, String adminName, String adminDiscordId) {
        var target = sessionService.getOrLoadFromDb(id);
        if (target == null) {
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        if (!muteDataRepository.delete(target.uuid)) {
            return ModerationResult.failure(MUTE_DELETE_FAILED_MESSAGE);
        }

        AuditRecord audit = appendAudit(
                AuditAction.UNMUTE,
                auditTarget(target.uuid, target.pid, target.nickname, null),
                legacyActor(adminName, adminDiscordId),
                legacyOrigin(adminName),
                DEFAULT_REASON,
                new AuditDetails(),
                null
        );

        postAuditEvent(audit);
        network.post(toPardonCommand(target.uuid, target.pid, target.nickname, null, audit));

        return ModerationResult.success("Player '" + target.nickname + "' unmuted successfully", target);
    }

    /**
     * Temporary ban a player by UUID or IP.
     *
     * @param uuid      Player UUID (can be null)
     * @param ip        Player IP (can be null)
     * @param name      Player name
     * @param duration  Ban duration period
     * @param reason    Ban reason
     * @param adminName Name of the admin performing the ban
     * @return Result containing BanData if successful
     */
    public ModerationResult<BanData> tempBanByUuidOrIp(String uuid, String ip, String name, Duration duration, String reason, String adminName, String adminDiscordId) {
        if (hasNoIdentifier(uuid, ip)) {
            return ModerationResult.failure(MISSING_IDENTIFIER_MESSAGE);
        }

        Instant expire = toExpireDate(duration);

        BanData ban = BanData.builder()
                .name(resolvePlayerName(name))
                .uuid(uuid)
                .ip(ip)
                .adminName(adminName)
                .adminDiscordId(resolveAdminDiscordId(adminDiscordId))
                .reason(resolveReason(reason))
                .expireDate(expire)
                .build();

        if (!banDataRepository.save(ban)) {
            return ModerationResult.failure(BAN_SAVE_FAILED_MESSAGE);
        }

        AuditRecord audit = appendAudit(
                AuditAction.BAN,
                auditTarget(uuid, null, ban.name, ip),
                legacyActor(adminName, adminDiscordId),
                legacyOrigin(adminName),
                ban.reason,
                auditDetails(duration, expire),
                null
        );

        if (hasUuid(uuid)) {
            postBanEvents(ban, audit);
        }
        postAuditEvent(audit);
        network.post(ModerationProtocolMapper.toKickBannedCommand(
                uuid,
                null,
                ban.name,
                ip,
                config.server,
                commandOccurredAt(audit)
        ));

        return ModerationResult.success("Player '" + ban.name + "' banned until " + expire, ban);
    }

    /**
     * Temporary unban by UUID or IP.
     *
     * @param uuid Player UUID (can be null)
     * @param ip   Player IP (can be null)
     * @return Result indicating success or failure
     */
    public ModerationResult<Void> tempUnban(String uuid, String ip, String adminName, String adminDiscordId) {
        if (hasNoIdentifier(uuid, ip)) {
            return ModerationResult.failure(MISSING_IDENTIFIER_MESSAGE);
        }

        if (!banDataRepository.delete(uuid, ip)) {
            return ModerationResult.failure(BAN_DELETE_FAILED_MESSAGE);
        }

        AuditRecord audit = appendAudit(
                AuditAction.UNBAN,
                auditTarget(uuid, null, UNKNOWN_PLAYER_NAME, ip),
                legacyActor(adminName, adminDiscordId),
                legacyOrigin(adminName),
                DEFAULT_REASON,
                new AuditDetails(),
                null
        );

        postAuditEvent(audit);
        network.post(toPardonCommand(uuid, null, UNKNOWN_PLAYER_NAME, ip, audit));

        return ModerationResult.success("Unbanned: UUID=" + uuid + " / IP=" + ip, null);
    }

    /**
     * Parse period string using TimeService.
     *
     * @param periodStr Period string (e.g., "1d", "2h")
     * @param unit      Time unit
     * @return Parsed Duration or null if invalid
     */
    public Duration parsePeriod(String periodStr, TimeUnit unit) {
        Instant parsed = time.parsePeriod(periodStr, unit);
        if (parsed == null) {
            return null;
        }
        return Duration.ofMillis(parsed.toEpochMilli());
    }

    /**
     * Find player data by UUID or PID.
     *
     * @param uuidOrPid UUID or PID string (PID starts with #)
     * @return PlayerData or null if not found
     */
    public PlayerData findPlayerData(String uuidOrPid) {
        return find.playerData(uuidOrPid);
    }

    private static String resolveReason(String reason) {
        return reason != null ? reason : DEFAULT_REASON;
    }

    private static String resolvePlayerName(String name) {
        return name != null ? name : UNKNOWN_PLAYER_NAME;
    }

    private static String resolveAdminDiscordId(String adminDiscordId) {
        return adminDiscordId != null ? adminDiscordId : EMPTY_DISCORD_ID;
    }

    private AuditRecord appendAudit(AuditAction action,
                                    AuditTarget target,
                                    AuditActor actor,
                                    AuditOrigin origin,
                                    String reason,
                                    AuditDetails details,
                                    String relatedAuditId) {
        var result = auditService.append(AuditAppendCommand.builder()
                .action(action)
                .target(target)
                .actor(actor)
                .origin(origin)
                .reason(reason)
                .details(details)
                .relatedAuditId(relatedAuditId)
                .build());
        return result.getRecord().orElse(null);
    }

    private void postAuditEvent(AuditRecord audit) {
        if (audit != null) {
            network.post(ModerationProtocolMapper.toAuditAppended(audit, config.server));
        }
    }

    private void postBanEvents(BanData ban, AuditRecord audit) {
        network.post(ModerationProtocolMapper.toBanCreated(ban, config.server, eventOccurredAt(audit)));
    }

    private ModerationPardonCommandV1 toPardonCommand(String uuid, Integer pid, String playerName, String ip, AuditRecord audit) {
        return ModerationProtocolMapper.toPardonCommand(
                uuid,
                pid,
                playerName,
                ip,
                config.server,
                commandOccurredAt(audit)
        );
    }

    private static Instant eventOccurredAt(AuditRecord audit) {
        return audit != null && audit.occurredAt != null ? audit.occurredAt : Instant.now();
    }

    private static Instant commandOccurredAt(AuditRecord audit) {
        return eventOccurredAt(audit);
    }

    private static AuditTarget auditTarget(String uuid, Integer pid, String nameSnapshot, String ipSnapshot) {
        return AuditTarget.builder()
                .uuid(uuid == null ? "" : uuid)
                .pid(pid)
                .nameSnapshot(resolvePlayerName(nameSnapshot))
                .ipSnapshot(ipSnapshot)
                .build();
    }

    private static AuditActor legacyActor(String adminName, String adminDiscordId) {
        String normalizedName = resolvePlayerName(adminName);
        String normalizedDiscordId = resolveAdminDiscordId(adminDiscordId);
        if ("console".equalsIgnoreCase(normalizedName)) {
            return AuditActor.builder()
                    .type(AuditActorType.SERVER_CONSOLE)
                    .id("console")
                    .nameSnapshot("console")
                    .serverId(null)
                    .build();
        }

        return AuditActor.builder()
                .type(AuditActorType.PLAYER_ADMIN)
                .id(!normalizedDiscordId.isBlank() ? normalizedDiscordId : normalizedName)
                .nameSnapshot(normalizedName)
                .discordId(normalizedDiscordId.isBlank() ? null : normalizedDiscordId)
                .build();
    }

    private static AuditOrigin legacyOrigin(String adminName) {
        if ("console".equalsIgnoreCase(resolvePlayerName(adminName))) {
            return AuditOrigin.builder()
                    .channel(AuditOriginChannel.SERVER_CONSOLE)
                    .source("xcore-plugin")
                    .build();
        }
        return AuditOrigin.builder()
                .channel(AuditOriginChannel.IN_GAME)
                .source("xcore-plugin")
                .build();
    }

    private static AuditDetails auditDetails(Duration duration, Instant expiresAt) {
        return AuditDetails.builder()
                .durationMs(duration == null ? null : duration.toMillis())
                .expiresAt(expiresAt)
                .build();
    }

    private static boolean hasNoIdentifier(String uuid, String ip) {
        return uuid == null && ip == null;
    }

    private static boolean hasUuid(String uuid) {
        return uuid != null && !uuid.isBlank();
    }

    private static Instant toExpireDate(Duration duration) {
        return Instant.now().plus(duration);
    }
}
