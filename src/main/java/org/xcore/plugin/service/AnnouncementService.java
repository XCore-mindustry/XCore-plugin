package org.xcore.plugin.service;

import arc.util.Log;
import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.Vars;
import mindustry.gen.Groups;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodically broadcasts server tips and announcements to connected players in their chosen locale.
 */
@Singleton
public class AnnouncementService {

    private final TomlXcoreConfig config;
    private final SessionService sessionService;
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    private Timer.Task scheduledTask;

    @Inject
    public AnnouncementService(TomlXcoreConfig config, SessionService sessionService) {
        this.config = Objects.requireNonNull(config, "config");
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService");
    }

    @PostConstruct
    public void start() {
        if (!config.announcements.enabled) {
            return;
        }

        float firstDelay = Math.max(1f, config.announcements.firstDelaySeconds);
        float interval = Math.max(10f, config.announcements.intervalSeconds);

        scheduledTask = Timer.schedule(this::broadcastNext, firstDelay, interval);
        Log.info("[AnnouncementService] Scheduled periodic announcements every @s (initial delay @s)",
                interval, firstDelay);
    }

    @PreDestroy
    public void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    /**
     * Broadcasts the next announcement in rotation to all active player sessions in their chosen language.
     *
     * @return the announcement key that was broadcast, or null if skipped
     */
    public String broadcastNext() {
        if (!config.announcements.enabled) {
            return null;
        }
        List<String> messages = config.announcements.messages;
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        if (Vars.state == null || !Vars.state.isGame()) {
            return null;
        }
        if (Groups.player == null || Groups.player.size() == 0) {
            return null;
        }

        int index = Math.floorMod(currentIndex.getAndIncrement(), messages.size());
        String messageKey = messages.get(index);

        broadcast(messageKey);
        return messageKey;
    }

    /**
     * Broadcasts a specific announcement key to all connected players in their personal locale.
     *
     * @param messageKey the Fluent localization key (e.g. "announcement-hub")
     * @return the number of players the message was sent to
     */
    public int broadcast(String messageKey) {
        if (messageKey == null || messageKey.isBlank()) {
            return 0;
        }

        int deliveredCount = 0;
        for (Session session : sessionService.getAllCachedSnapshot()) {
            if (session == null || session.player == null || session.player.con == null) {
                continue;
            }
            try {
                session.locale().send(messageKey);
                deliveredCount++;
            } catch (Exception e) {
                Log.err("[AnnouncementService] Failed to send announcement '@' to player '@': @",
                        messageKey, session.player.name, e.getMessage());
            }
        }
        return deliveredCount;
    }

    public int currentIndex() {
        return currentIndex.get();
    }

    public void resetIndex() {
        currentIndex.set(0);
    }
}
