package org.xcore.plugin.service;

import arc.func.Cons3;
import arc.util.Timer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.session.SessionService;

import java.util.Locale;

@Singleton
public class LeaderboardService {

    private final SessionService sessionService;

    @Inject
    public LeaderboardService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void start(Cons3<StringBuilder, Player, Locale> contentGenerator) {
        Timer.schedule(() -> {
            if (Groups.player.isEmpty()) return;

            Groups.player.each(player -> {
                var session = sessionService.get(player);
                var data = session.data;
                if (data == null || !data.leaderboard) return;

                StringBuilder builder = new StringBuilder();
                Locale locale = session.locale().getLocale();

                contentGenerator.get(builder, player, locale);

                if (!builder.isEmpty()) {
                    Call.infoPopup(player.con, builder.toString(), 5f, 8, 0, 2, 50, 0);
                }
            });
        }, 0f, 5f);
    }
}
