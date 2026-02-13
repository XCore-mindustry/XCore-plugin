package org.xcore.plugin.service;

import arc.func.Cons3;
import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.localization.BundleService;
import org.xcore.plugin.session.SessionService;

import java.util.Locale;

@Singleton
public class LeaderboardService {

    private final SessionService sessionService;
    private final BundleService bundle;

    @Inject
    public LeaderboardService(SessionService sessionService, BundleService bundle) {
        this.sessionService = sessionService;
        this.bundle = bundle;
    }

    public void start(Cons3<StringBuilder, Player, Locale> contentGenerator) {
        Timer.schedule(() -> {
            if (Groups.player.isEmpty()) return;

            Groups.player.each(player -> {
                var data = sessionService.get(player.uuid()).data;
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
