package org.xcore.plugin.service;

import arc.util.Timer;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.startup.ShutdownOrder;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerActivityService {

    private final SessionService sessionService;
    private final FindService findService;
    private final TomlSecretsConfig secretsConfig;
    private Timer.Task activityTask;
    private volatile boolean stopped;

    @Inject
    public PlayerActivityService(SessionService sessionService,
                                 FindService findService,
                                 TomlSecretsConfig secretsConfig) {
        this.sessionService = sessionService;
        this.findService = findService;
        this.secretsConfig = secretsConfig;
    }

    @PostConstruct
    public void start() {
        activityTask = Timer.schedule(() -> {
            if (stopped) return;
            for (Session session : sessionService.getAllCachedSnapshot()) {
                if (session == null || session.data == null) continue;
                var player = findService.playerByUuid(session.data.uuid);
                if (player == null) continue;

                PlayerData data = session.data;
                Localization local = session.locale();

                sessionService.incrementPlayTimeAsync(session, 1);

                if (data.totalPlayTime == secretsConfig.moderation.votekick.minPlayTimeMinutes) {
                    local.send("notification-votekick-playtime",
                            args("votekickPlayTime", secretsConfig.moderation.votekick.minPlayTimeMinutes));
                }
                if (data.totalPlayTime == secretsConfig.chat.global.minPlayTimeMinutes) {
                    local.send("notification-global-chat-playtime",
                            args("globalChatPlayTime", secretsConfig.chat.global.minPlayTimeMinutes));
                }

            }
        }, 0, 60);
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        stopped = true;
        if (activityTask != null) {
            activityTask.cancel();
            activityTask = null;
        }
    }
}
