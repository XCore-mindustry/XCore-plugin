package org.xcore.plugin.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import arc.util.Strings;
import org.bson.types.ObjectId;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PrivateMessageRepository;
import org.xcore.plugin.event.SocketEvents;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PrivateMessage;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PrivateMessageService {

    private final PrivateMessageRepository privateMessageRepository;
    private final SessionService sessionService;
    private final SecurityService securityService;
    private final NetworkService networkService;
    private final Config config;
    private final GlobalConfig globalConfig;

    @Inject
    public PrivateMessageService(PrivateMessageRepository privateMessageRepository,
                                 SessionService sessionService,
                                 SecurityService securityService,
                                 NetworkService networkService,
                                 Config config,
                                 GlobalConfig globalConfig) {
        this.privateMessageRepository = privateMessageRepository;
        this.sessionService = sessionService;
        this.securityService = securityService;
        this.networkService = networkService;
        this.config = config;
        this.globalConfig = globalConfig;
    }

    public boolean send(Session senderSession, int targetPid, String rawMessage) {
        if (!hasActiveSender(senderSession)) {
            return false;
        }

        if (securityService.isMuted(senderSession.player)) {
            return false;
        }

        PlayerData targetData = resolveTargetByPid(targetPid);
        if (targetData == null) {
            senderSession.locale().send("error-player-not-found", args());
            return false;
        }

        if (Objects.equals(targetData.uuid, senderSession.data.uuid)) {
            senderSession.locale().send("error-private-message-self", args());
            return false;
        }

        String message = normalizeMessage(rawMessage);
        if (message == null) {
            senderSession.locale().send("error-private-message-empty", args());
            return false;
        }

        if (message.length() > globalConfig.privateMessageMaxLength) {
            senderSession.locale().send("error-private-message-too-long", args("max", globalConfig.privateMessageMaxLength));
            return false;
        }

        if (isRateLimited(senderSession)) {
            long remaining = cooldownRemainingSeconds(senderSession);
            senderSession.locale().send("error-private-message-cooldown", args("seconds", remaining));
            return false;
        }

        if (targetData.blockedPrivateUuids != null && targetData.blockedPrivateUuids.contains(senderSession.data.uuid)) {
            senderSession.locale().send("error-private-message-target-unavailable", args());
            return false;
        }

        if (privateMessageRepository.countUnread(targetData.uuid) >= globalConfig.privateMessageUnreadLimit) {
            senderSession.locale().send("error-private-message-target-unavailable", args());
            return false;
        }

        PrivateMessage privateMessage = PrivateMessage.builder()
                .fromUuid(senderSession.data.uuid)
                .fromPid(senderSession.data.pid)
                .fromName(senderSession.player.coloredName())
                .toUuid(targetData.uuid)
                .toPid(targetData.pid)
                .message(message)
                .build();

        if (!privateMessageRepository.save(privateMessage)) {
            senderSession.locale().send("error-processing-request", args());
            return false;
        }

        deliverOrDispatch(privateMessage, senderSession.data.pid);

        senderSession.lastPrivateTargetPid = targetData.pid;
        senderSession.lastPrivateMessageAt = System.currentTimeMillis();
        senderSession.locale().send("private-message-sent", args(
                "target", targetData.nickname,
                "pid", targetData.pid,
                "message", message
        ));
        return true;
    }

    public boolean reply(Session senderSession, String message) {
        PlayerData target = resolveLastCorrespondent(senderSession);
        if (target == null) {
            senderSession.locale().send("error-private-message-no-reply-target", args());
            return false;
        }

        return send(senderSession, target.pid, message);
    }

    public long countUnread(String uuid) {
        return privateMessageRepository.countUnread(uuid);
    }

    public long countInbox(String uuid) {
        return privateMessageRepository.countInbox(uuid);
    }

    public List<PrivateMessage> inbox(String uuid, int page) {
        int safePage = Math.max(1, page);
        int limit = Math.max(1, globalConfig.privateMessagesPerPage);
        int skip = (safePage - 1) * limit;
        return privateMessageRepository.findInbox(uuid, skip, limit);
    }

    public PrivateMessage getMessage(ObjectId id, String recipientUuid) {
        var message = privateMessageRepository.findById(id);
        if (message == null || !Objects.equals(message.toUuid, recipientUuid) || message.recipientDeleted) {
            return null;
        }
        return message;
    }

    public boolean markRead(ObjectId messageId, String recipientUuid) {
        PrivateMessage message = getMessage(messageId, recipientUuid);
        if (message == null || message.readAt > 0) {
            return false;
        }

        message.readAt = System.currentTimeMillis();
        privateMessageRepository.save(message);
        return true;
    }

    public boolean softDelete(ObjectId messageId, String recipientUuid) {
        PrivateMessage message = getMessage(messageId, recipientUuid);
        if (message == null) {
            return false;
        }

        message.recipientDeleted = true;
        privateMessageRepository.save(message);
        return true;
    }

    public boolean block(Session session, int targetPid) {
        if (!hasSessionData(session)) {
            return false;
        }

        PlayerData targetData = resolveTargetByPid(targetPid);
        if (targetData == null) {
            session.locale().send("error-player-not-found", args());
            return false;
        }

        if (Objects.equals(targetData.uuid, session.data.uuid)) {
            session.locale().send("error-private-message-block-self", args());
            return false;
        }

        if (session.data.blockedPrivateUuids == null) {
            session.data.blockedPrivateUuids = new java.util.HashSet<>();
        }

        if (session.data.blockedPrivateUuids.contains(targetData.uuid)) {
            session.locale().send("private-message-block-already", args("target", targetData.nickname, "pid", targetData.pid));
            return false;
        }

        if (session.data.blockedPrivateUuids.size() >= globalConfig.privateMessageBlockedLimit) {
            session.locale().send("error-private-message-block-limit", args("limit", globalConfig.privateMessageBlockedLimit));
            return false;
        }

        sessionService.addBlockedPrivateUuid(session, targetData.uuid);
        session.locale().send("private-message-block-success", args("target", targetData.nickname, "pid", targetData.pid));
        return true;
    }

    public boolean unblock(Session session, int targetPid) {
        if (!hasSessionData(session)) {
            return false;
        }

        PlayerData targetData = resolveTargetByPid(targetPid);
        if (targetData == null) {
            session.locale().send("error-player-not-found", args());
            return false;
        }

        if (session.data.blockedPrivateUuids == null || !session.data.blockedPrivateUuids.contains(targetData.uuid)) {
            session.locale().send("private-message-unblock-missing", args("target", targetData.nickname, "pid", targetData.pid));
            return false;
        }

        sessionService.removeBlockedPrivateUuid(session, targetData.uuid);
        session.locale().send("private-message-unblock-success", args("target", targetData.nickname, "pid", targetData.pid));
        return true;
    }

    public List<PlayerData> listBlocked(Session session) {
        if (session == null || session.data == null || session.data.blockedPrivateUuids == null || session.data.blockedPrivateUuids.isEmpty()) {
            return List.of();
        }

        List<PlayerData> result = new ArrayList<>();
        for (String uuid : session.data.blockedPrivateUuids) {
            PlayerData data = sessionService.getOrLoadFromDb(uuid);
            if (data != null) {
                result.add(data);
            }
        }

        result.sort(Comparator.comparingInt(value -> value.pid));
        return result;
    }

    public PlayerData resolveLastCorrespondent(Session session) {
        if (!hasSessionData(session)) {
            return null;
        }

        if (session.lastPrivateTargetPid != null) {
            PlayerData fromSession = sessionService.getOrLoadFromDb(session.lastPrivateTargetPid);
            if (fromSession != null) {
                return fromSession;
            }
        }

        PrivateMessage latest = privateMessageRepository.findLatestConversationMessage(session.data.uuid);
        if (latest == null) {
            return null;
        }

        String otherUuid = Objects.equals(latest.fromUuid, session.data.uuid) ? latest.toUuid : latest.fromUuid;
        PlayerData data = sessionService.getOrLoadFromDb(otherUuid);
        if (data != null) {
            session.lastPrivateTargetPid = data.pid;
        }
        return data;
    }

    public void deliverIncoming(PrivateMessage message, Session recipientSession) {
        if (recipientSession == null || recipientSession.player == null) {
            return;
        }

        recipientSession.locale().send("private-message-received", args(
                "author", message.fromName,
                "pid", message.fromPid,
                "message", message.message
        ));
    }

    public Integer parseMenuPid(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized = input.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        int parsed = Strings.parseInt(normalized, Integer.MIN_VALUE);
        return parsed == Integer.MIN_VALUE || parsed < 0 ? null : parsed;
    }

    private boolean isRateLimited(Session session) {
        long diff = System.currentTimeMillis() - session.lastPrivateMessageAt;
        return diff < globalConfig.privateMessageCooldownSeconds * 1000L;
    }

    private long cooldownRemainingSeconds(Session session) {
        long remainingMillis = globalConfig.privateMessageCooldownSeconds * 1000L - (System.currentTimeMillis() - session.lastPrivateMessageAt);
        return Math.max(1L, (long) Math.ceil(remainingMillis / 1000.0));
    }

    private boolean hasActiveSender(Session session) {
        return hasSessionData(session) && session.player != null;
    }

    private boolean hasSessionData(Session session) {
        return session != null && session.data != null;
    }

    private PlayerData resolveTargetByPid(int targetPid) {
        PlayerData targetData = sessionService.getOrLoadFromDb(targetPid);
        if (targetData == null || targetData.uuid == null || targetData.uuid.isBlank()) {
            return null;
        }
        return targetData;
    }

    private void deliverOrDispatch(PrivateMessage privateMessage, int senderPid) {
        Session recipientSession = sessionService.get(privateMessage.toUuid);
        if (recipientSession != null && recipientSession.player != null) {
            privateMessage.deliveredAt = System.currentTimeMillis();
            privateMessageRepository.save(privateMessage);
            deliverIncoming(privateMessage, recipientSession);
            recipientSession.lastPrivateTargetPid = senderPid;
            return;
        }

        networkService.post(new SocketEvents.PrivateMessageEvent(
                privateMessage.fromUuid,
                privateMessage.fromPid,
                privateMessage.fromName,
                privateMessage.toUuid,
                privateMessage.toPid,
                privateMessage.message,
                config.server
        ));
    }

    private String normalizeMessage(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }

        String normalized = rawMessage.trim().replace('`', '*');
        return normalized.isBlank() ? null : normalized;
    }
}
