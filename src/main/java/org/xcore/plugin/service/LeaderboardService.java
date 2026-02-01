package org.xcore.plugin.service;

import arc.func.Cons3;
import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import java.util.Locale;

@Singleton
public class LeaderboardService {

    private final PlayerSessionService playerSessionService;
    private final BundleService bundle;

    @Inject
    public LeaderboardService(PlayerSessionService playerSessionService, BundleService bundle) {
        this.playerSessionService = playerSessionService;
        this.bundle = bundle;
    }

    public void start(Cons3<StringBuilder, Player, Locale> contentGenerator) {
        Timer.schedule(() -> {
            if (Groups.player.isEmpty()) return;

            Groups.player.each(player -> {
                var data = playerSessionService.get(player.uuid());
                if (data == null || !data.leaderboard) return;

                StringBuilder builder = new StringBuilder();
                Locale locale = bundle.locale(player);

                contentGenerator.get(builder, player, locale);

                if (!builder.isEmpty()) {
                    Call.infoPopup(player.con, builder.toString(), 5f, 8, 0, 2, 50, 0);
                }
            });
        }, 0f, 5f);
    }
}
