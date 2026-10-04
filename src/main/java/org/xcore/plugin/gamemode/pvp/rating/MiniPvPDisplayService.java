package org.xcore.plugin.gamemode.pvp.rating;

import arc.Events;
import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.integration.PlayerDisplayRefreshService;
import org.xcore.plugin.integration.PlayerDisplayRegistry;
import org.xcore.plugin.rating.ladder.LadderLeagueDisplay;

@Singleton
public class MiniPvPDisplayService {
    private static final String PROVIDER_ID = "minipvp-league";

    private final TomlXcoreConfig config;
    private final PlayerDisplayRegistry registry;
    private final LadderLeagueDisplay display;
    private PlayerDisplayRegistry.Registration registration;

    @Inject
    public MiniPvPDisplayService(
            TomlXcoreConfig config,
            PlayerDisplayRegistry registry,
            PlayerDisplayRefreshService refreshService,
            MiniPvPLadder miniPvPLadder,
            Async async
    ) {
        this.config = config;
        this.registry = registry;
        this.display = new LadderLeagueDisplay(PROVIDER_ID, 10, miniPvPLadder.ladder(), refreshService, async);
    }

    @PostConstruct
    public void init() {
        if (!"mini-pvp".equals(config.server.name)) return;

        start();
        Events.on(EventType.PlayerJoin.class, e -> display.preload(e.player));
    }

    public void start() {
        if (registration != null) return;
        registration = registry.register(display);
    }

    public void stop() {
        if (registration != null) {
            registration.close();
            registration = null;
        }
    }
}
