package org.xcore.plugin.gamemode.pvp.rating;

import io.avaje.inject.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.integration.PlayerDisplayProvider;
import org.xcore.plugin.integration.PlayerDisplayRegistry;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.rating.RatingLeague;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

@Singleton
public class MiniPvPDisplayService {
    private static final String PROVIDER_ID = "minipvp-league";

    private final TomlXcoreConfig config;
    private final PlayerDisplayRegistry registry;
    private final SessionService sessionService;
    private PlayerDisplayRegistry.Registration registration;

    @Inject
    public MiniPvPDisplayService(
            TomlXcoreConfig config,
            PlayerDisplayRegistry registry,
            SessionService sessionService
    ) {
        this.config = config;
        this.registry = registry;
        this.sessionService = sessionService;
    }

    @PostConstruct
    public void init() {
        if (!"mini-pvp".equals(config.server.name)) return;

        start();
    }

    public void start() {
        if (registration != null) return;

        registration = registry.register(new PlayerDisplayProvider() {
            @Override
            public String id() {
                return PROVIDER_ID;
            }

            @Override
            public int priority() {
                return 10;
            }

            @Override
            public String resolve(PlayerData data, Player player) {
                int rating = 0;
                if (data != null && data.pvpRating > 0) {
                    rating = data.pvpRating;
                } else if (player != null && player.uuid() != null) {
                    Session s = sessionService.get(player);
                    if (s != null && s.data != null) {
                        rating = s.data.pvpRating;
                    }
                }

                if (rating <= 0) return "";
                return RatingLeague.fromRating(rating).icon();
            }
        });
    }

    public void stop() {
        if (registration != null) {
            registration.close();
            registration = null;
        }
    }
}
