package org.xcore.plugin.service;

import arc.func.Cons3;
import arc.struct.IntSet;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.startup.ShutdownOrder;

@Singleton
public class LeaderboardService {

    /** The client keeps one popup per id, so every refresh replaces the previous one in place. */
    static final String POPUP_ID = "xcore-leaderboard";
    static final float REFRESH_SECONDS = 5f;
    /** The popup packet is unreliable; this lets the popup survive one lost refresh without a gap. */
    static final float POPUP_SECONDS = REFRESH_SECONDS * 2f + 1f;

    private final SessionService sessionService;
    private final ScheduledTasks tasks = new ScheduledTasks(LeaderboardService.class);
    private ScheduledTasks.Handle refreshTask;
    private IntSet shown = new IntSet();

    @Inject
    public LeaderboardService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public void start(Cons3<StringBuilder, Player, Localization> contentGenerator) {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = tasks.every(0f, REFRESH_SECONDS, () -> refresh(contentGenerator));
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void stop() {
        tasks.close();
    }

    public void hide(Player player) {
        shown.remove(player.id);
        Call.infoPopupReliable(player.con, null, POPUP_ID, 0f, 8, 0, 2, 50, 0);
    }

    void refresh(Cons3<StringBuilder, Player, Localization> contentGenerator) {
        IntSet showing = new IntSet();
        Groups.player.each(player -> {
            var session = sessionService.get(player);
            var data = session == null ? null : session.data;
            StringBuilder builder = new StringBuilder();
            if (data != null && data.leaderboard) {
                contentGenerator.get(builder, player, session.locale());
            }

            if (!builder.isEmpty()) {
                showing.add(player.id);
                Call.infoPopup(player.con, builder.toString(), POPUP_ID, POPUP_SECONDS, 8, 0, 2, 50, 0);
            } else if (shown.contains(player.id)) {
                // Also handle the preference being changed through the settings menu.
                Call.infoPopupReliable(player.con, null, POPUP_ID, 0f, 8, 0, 2, 50, 0);
            }
        });
        shown = showing;
    }
}
