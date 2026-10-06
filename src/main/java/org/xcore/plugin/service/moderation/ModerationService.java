package org.xcore.plugin.service.moderation;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlXcoreConfig;
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
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.permission.Actor;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.service.NetworkService;
import org.xcore.plugin.service.SecurityService;
import org.xcore.plugin.service.TimeService;
import org.xcore.plugin.service.network.ModerationProtocolMapper;
import org.xcore.plugin.session.SessionService;
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
    public static final String PLAYER_NOT_FOUND_MESSAGE = "Player not found";
    public static final String MISSING_ACTOR_MESSAGE = "Moderation actor is missing";
    private static final String DEFAULT_REASON = "Not Specified";
    private static final String UNKNOWN_PLAYER_NAME = "Unknown";
    private static final String EMPTY_DISCORD_ID = "";
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
    private final TomlXcoreConfig config;
    private final jakarta.inject.Provider<SecurityService> securityService;

    @Inject
    public ModerationService(PlayerDataRepository playerDataRepository,
                             BanDataRepository banDataRepository,
                             MuteDataRepository muteDataRepository,
                             SessionService sessionService,
                             NetworkService network,
                             FindService find,
                             TimeService timeService,
                             AuditService auditService,
                             TomlXcoreConfig config,
                             jakarta.inject.Provider<SecurityService> securityService) {
        this.playerDataRepository = playerDataRepository;
        this.banDataRepository = banDataRepository;
        this.muteDataRepository = muteDataRepository;
        this.sessionService = sessionService;
        this.network = network;
        this.find = find;
        this.time = timeService;
        this.auditService = auditService;
        this.config = config;
        this.securityService = securityService;
    }

    public ModerationService(PlayerDataRepository playerDataRepository,
                             BanDataRepository banDataRepository,
                             MuteDataRepository muteDataRepository,
                             SessionService sessionService,
                             NetworkService network,
                             FindService find,
                             TimeService timeService,
                             AuditService auditService,
                             TomlXcoreConfig config) {
        this(playerDataRepository, banDataRepository, muteDataRepository, sessionService, network, find, timeService, auditService, config, () -> null);
    }

    /**
     * Executes a unified ban command.
     */
    public ModerationResult<BanData> ban(BanCommand command) {
        if (command == null) {
            return ModerationResult.failure("Invalid ban command");
        }
        ModerationActor actor = command.actor();
        if (actor == null) {
            return ModerationResult.failure(MISSING_ACTOR_MESSAGE);
        }
        if (PlayerPids.isAssigned(command.targetId())) {
            return banById(command.targetId(), actor.name(), actor.discordId(),
                    command.reason(), command.duration(), command.kickOnline());
        }
        return tempBanByUuidOrIp(command.targetUuid(), command.targetIp(), command.targetName(),
                command.duration(), command.reason(), actor.name(), actor.discordId());
    }

    /**
     * Executes a unified unban command.
     */
    public ModerationResult<PlayerData> unban(UnbanCommand command) {
        if (command == null) {
            return ModerationResult.failure("Invalid unban command");
        }
        ModerationActor actor = command.actor();
        if (actor == null) {
            return ModerationResult.failure(MISSING_ACTOR_MESSAGE);
        }
        if (PlayerPids.isAssigned(command.targetId())) {
            return unbanById(command.targetId(), actor.name(), actor.discordId());
        }
        var res = tempUnban(command.targetUuid(), command.targetIp(), actor.name(), actor.discordId());
        if (!res.isSuccess()) {
            return ModerationResult.failure(res.getMessage().orElse("Failed to unban"));
        }
        return ModerationResult.success(res.getMessage().orElse("Unbanned"), null);
    }

    /**
     * Executes a unified mute command.
     */
    public ModerationResult<MuteData> mute(MuteCommand command) {
        if (command == null) {
            return ModerationResult.failure("Invalid mute command");
        }
        ModerationActor actor = command.actor();
        if (actor == null) {
            return ModerationResult.failure(MISSING_ACTOR_MESSAGE);
        }
        Integer pid = resolveTargetPid(command.targetId(), command.targetUuid());
        if (pid != null) {
            return muteById(pid, actor.name(), actor.discordId(), command.reason(), command.duration());
        }
        return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
    }

    /**
     * Executes a unified unmute command.
     */
    public ModerationResult<PlayerData> unmute(UnmuteCommand command) {
        if (command == null) {
            return ModerationResult.failure("Invalid unmute command");
        }
        ModerationActor actor = command.actor();
        if (actor == null) {
            return ModerationResult.failure(MISSING_ACTOR_MESSAGE);
        }
        Integer pid = resolveTargetPid(command.targetId(), command.targetUuid());
        if (pid != null) {
            return unmuteById(pid, actor.name(), actor.discordId());
        }
        return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
    }

    /**
     * Знаходить PlayerData за будь-яким ідентифікатором:
     * 1. @Steve -> по юзернейму
     * 2. #123 або 123 -> по PID
     * 3. Steve -> по юзернейму
     * 4. UUID рядок -> по UUID
     */
    public PlayerData resolvePlayerData(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        target = target.trim();

        if (target.startsWith("@") && target.length() > 1) {
            return playerDataRepository.findByUsername(target.substring(1));
        }

        Integer pid = PlayerPids.parse(target);
        if (pid != null) {
            PlayerData data = playerDataRepository.findByPid(pid);
            if (data != null) return data;
        }

        PlayerData byUsername = playerDataRepository.findByUsername(target);
        if (byUsername != null) {
            return byUsername;
        }

        return playerDataRepository.findByUuid(target);
    }

    private Integer resolveTargetPid(int targetId, String targetUuid) {
        if (PlayerPids.isAssigned(targetId)) {
            return targetId;
        }
        if (targetUuid != null && !targetUuid.isBlank()) {
            var target = sessionService.getOrLoadFromDb(targetUuid);
            if (target != null) {
                return target.pid;
            }
        }
        return null;
    }
    
    public ModerationResult<BanData> banPlayer(PlayerData target, String adminName, String adminDiscordId, String reason, Duration duration, boolean kickOnline) {
        if (target == null) {
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        var info = netServer.admins.getInfoOptional(target.uuid);
        String ip = (info != null) ? info.lastIP : target.ip;

        return executeBan(target.uuid, ip, target.pid, target.nickname, duration, reason,
                adminName, adminDiscordId, kickOnline, false);
    }

    public ModerationResult<BanData> banByTarget(String target, String adminName, String adminDiscordId, String reason, Duration duration, boolean kickOnline) {
        return banPlayer(resolvePlayerData(target), adminName, adminDiscordId, reason, duration, kickOnline);
    }

    public ModerationResult<BanData> banById(int id, String adminName, String adminDiscordId, String reason, Duration duration, boolean kickOnline) {
        return banPlayer(playerDataRepository.findByPid(id), adminName, adminDiscordId, reason, duration, kickOnline);
    }

    private ModerationResult<BanData> executeBan(String uuid, String ip, Integer pid, String name,
                                                 Duration duration, String reason, String adminName,
                                                 String adminDiscordId, boolean kickOnline, boolean isTempBan) {
        Instant expire = toExpireDate(duration);
        String playerName = resolvePlayerName(name);

        BanData ban = BanData.builder()
                .name(playerName)
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
                auditTarget(uuid, pid, playerName, ip),
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

        if (kickOnline) {
            network.post(ModerationProtocolMapper.toKickBannedCommand(
                    uuid,
                    pid,
                    playerName,
                    ip,
                    config.server.name,
                    eventOccurredAt(audit)
            ));
        }

        String message = isTempBan && expire != null
                ? "Player '" + playerName + "' banned until " + expire
                : "Player '" + playerName + "' banned successfully";
        return ModerationResult.success(message, ban);
    }
    
    public ModerationResult<PlayerData> unbanPlayer(PlayerData target, String adminName, String adminDiscordId) {
        if (target == null) {
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        if (!banDataRepository.delete(target.uuid, null)) {
            return ModerationResult.failure(BAN_DELETE_FAILED_MESSAGE);
        }

        auditAndPublishPardon(AuditAction.UNBAN, target.uuid, target.pid, target.nickname, null, adminName, adminDiscordId);

        return ModerationResult.success("Player '" + target.nickname + "' unbanned successfully", target);
    }

    public ModerationResult<PlayerData> unbanByTarget(String target, String adminName, String adminDiscordId) {
        return unbanPlayer(resolvePlayerData(target), adminName, adminDiscordId);
    }

    public ModerationResult<PlayerData> unbanById(int id, String adminName, String adminDiscordId) {
        return unbanPlayer(playerDataRepository.findByPid(id), adminName, adminDiscordId);
    }

    private void auditAndPublishPardon(AuditAction action, String uuid, Integer pid, String name, String ip,
                                       String adminName, String adminDiscordId) {
        AuditRecord audit = appendAudit(
                action,
                auditTarget(uuid, pid, name, ip),
                legacyActor(adminName, adminDiscordId),
                legacyOrigin(adminName),
                DEFAULT_REASON,
                new AuditDetails(),
                null
        );

        postAuditEvent(audit);
        network.post(toPardonCommand(uuid, pid, name, ip, audit));
    }
    
    public ModerationResult<MuteData> mutePlayer(PlayerData target, String adminName, String adminDiscordId, String reason, Duration duration) {
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

        network.post(ModerationProtocolMapper.toMuteCreated(mute, config.server.name, eventOccurredAt(audit)));
        postAuditEvent(audit);

        SecurityService sec = security();
        if (sec != null) {
            sec.setMuted(target.uuid, mute);
        }

        return ModerationResult.success("Player '" + target.nickname + "' muted successfully", mute);
    }

    public ModerationResult<MuteData> muteByTarget(String target, String adminName, String adminDiscordId, String reason, Duration duration) {
        return mutePlayer(resolvePlayerData(target), adminName, adminDiscordId, reason, duration);
    }

    public ModerationResult<MuteData> muteById(int id, String adminName, String adminDiscordId, String reason, Duration duration) {
        return mutePlayer(sessionService.getOrLoadFromDb(id), adminName, adminDiscordId, reason, duration);
    }
    
    public ModerationResult<PlayerData> unmutePlayer(PlayerData target, String adminName, String adminDiscordId) {
        if (target == null) {
            return ModerationResult.failure(PLAYER_NOT_FOUND_MESSAGE);
        }

        if (!muteDataRepository.delete(target.uuid)) {
            return ModerationResult.failure(MUTE_DELETE_FAILED_MESSAGE);
        }

        auditAndPublishPardon(AuditAction.UNMUTE, target.uuid, target.pid, target.nickname, null, adminName, adminDiscordId);

        SecurityService sec = security();
        if (sec != null) {
            sec.clearMute(target.uuid);
        }

        return ModerationResult.success("Player '" + target.nickname + "' unmuted successfully", target);
    }

    public ModerationResult<PlayerData> unmuteByTarget(String target, String adminName, String adminDiscordId) {
        return unmutePlayer(resolvePlayerData(target), adminName, adminDiscordId);
    }

    public ModerationResult<PlayerData> unmuteById(int id, String adminName, String adminDiscordId) {
        return unmutePlayer(sessionService.getOrLoadFromDb(id), adminName, adminDiscordId);
    }

    private SecurityService security() {
        return securityService != null ? securityService.get() : null;
    }

    public ModerationResult<BanData> tempBanByUuidOrIp(String uuid, String ip, String name, Duration duration, String reason, String adminName, String adminDiscordId) {
        if (hasNoIdentifier(uuid, ip)) {
            return ModerationResult.failure(MISSING_IDENTIFIER_MESSAGE);
        }

        return executeBan(uuid, ip, null, name, duration, reason,
                adminName, adminDiscordId, true, true);
    }

    public ModerationResult<Void> tempUnban(String uuid, String ip, String adminName, String adminDiscordId) {
        if (hasNoIdentifier(uuid, ip)) {
            return ModerationResult.failure(MISSING_IDENTIFIER_MESSAGE);
        }

        if (!banDataRepository.delete(uuid, ip)) {
            return ModerationResult.failure(BAN_DELETE_FAILED_MESSAGE);
        }

        auditAndPublishPardon(AuditAction.UNBAN, uuid, null, UNKNOWN_PLAYER_NAME, ip, adminName, adminDiscordId);

        return ModerationResult.success("Unbanned: UUID=" + uuid + " / IP=" + ip, null);
    }

    public Duration parsePeriod(String periodStr, TimeUnit unit) {
        Instant parsed = time.parsePeriod(periodStr, unit);
        if (parsed == null) {
            return null;
        }
        return Duration.ofMillis(parsed.toEpochMilli());
    }

    public PlayerData findPlayerData(String uuidOrPid) {
        return find.playerData(uuidOrPid);
    }

    private static String resolveReason(String reason) {
        return (reason != null && !reason.isBlank()) ? reason : DEFAULT_REASON;
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
            network.post(ModerationProtocolMapper.toAuditAppended(audit, config.server.name));
        }
    }

    private void postBanEvents(BanData ban, AuditRecord audit) {
        network.post(ModerationProtocolMapper.toBanCreated(ban, config.server.name, eventOccurredAt(audit)));
    }

    private ModerationPardonCommandV1 toPardonCommand(String uuid, Integer pid, String playerName, String ip, AuditRecord audit) {
        return ModerationProtocolMapper.toPardonCommand(
                uuid,
                pid,
                playerName,
                ip,
                config.server.name,
                eventOccurredAt(audit)
        );
    }

    private static Instant eventOccurredAt(AuditRecord audit) {
        return audit != null && audit.occurredAt != null ? audit.occurredAt : Instant.now();
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
        if (isRemoteConsole(normalizedName)) {
            return AuditActor.builder()
                    .type(AuditActorType.SERVER_CONSOLE)
                    .id(normalizedName)
                    .nameSnapshot(normalizedName)
                    .serverId(normalizedName.substring(Actor.RemoteConsole.AUDIT_PREFIX.length()))
                    .build();
        }

        return AuditActor.builder()
                .type(AuditActorType.PLAYER_ADMIN)
                .id(!normalizedDiscordId.isBlank() ? normalizedDiscordId : normalizedName)
                .nameSnapshot(normalizedName)
                .discordId(normalizedDiscordId.isBlank() ? null : normalizedDiscordId)
                .build();
    }

    /** A console command relayed from another server; see {@link Actor.RemoteConsole}. */
    private static boolean isRemoteConsole(String actorName) {
        return actorName.startsWith(Actor.RemoteConsole.AUDIT_PREFIX);
    }

    private static AuditOrigin legacyOrigin(String adminName) {
        String normalizedName = resolvePlayerName(adminName);
        AuditOriginChannel channel = "console".equalsIgnoreCase(normalizedName) || isRemoteConsole(normalizedName)
                ? AuditOriginChannel.SERVER_CONSOLE
                : AuditOriginChannel.IN_GAME;
        return AuditOrigin.builder()
                .channel(channel)
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
        return duration != null ? Instant.now().plus(duration) : null;
    }
}