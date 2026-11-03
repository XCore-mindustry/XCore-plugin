package org.xcore.plugin.service;

import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType.BlockBuildBeginEvent;
import mindustry.game.EventType.PlayerChatEvent;
import mindustry.gen.Player;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.event.EventSubscriptions;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.startup.ShutdownOrder;

import java.util.Objects;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerActivityService {

    /** Unchanged minutes after which a player stops earning play time, which gates votekick and global chat. */
    static final int AFK_AFTER_MINUTES = 5;

    private final SessionService sessionService;
    private final FindService findService;
    private final TomlSecretsConfig secretsConfig;
    private final ScheduledTasks tasks = new ScheduledTasks(PlayerActivityService.class);
    private final EventSubscriptions subscriptions = new EventSubscriptions();

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
        subscriptions.on(PlayerChatEvent.class, event -> markActive(event.player));
        subscriptions.on(BlockBuildBeginEvent.class, event -> {
            if (event.unit != null) markActive(event.unit.getPlayer());
        });
        tasks.every(0, 60, () -> {
            for (Session session : sessionService.getAllCachedSnapshot()) {
                if (session == null || session.data == null) continue;
                var player = findService.playerByUuid(session.data.uuid);
                if (player == null || idle(session, player)) continue;

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
        });
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        tasks.close();
        subscriptions.close();
    }

    private void markActive(Player player) {
        if (player == null) return;
        var session = sessionService.get(player.uuid());
        if (session != null) session.activeSinceTick = true;
    }

    /** Called once a minute; true once the player has neither moved, aimed, chatted nor built for {@link #AFK_AFTER_MINUTES}. */
    static boolean idle(Session session, Player player) {
        int pose = Objects.hash(player.tileX(), player.tileY(),
                (int) player.mouseX >> 3, (int) player.mouseY >> 3, player.shooting);
        session.idleMinutes = pose == session.lastPose && !session.activeSinceTick ? session.idleMinutes + 1 : 0;
        session.lastPose = pose;
        session.activeSinceTick = false;
        return session.idleMinutes >= AFK_AFTER_MINUTES;
    }
}
