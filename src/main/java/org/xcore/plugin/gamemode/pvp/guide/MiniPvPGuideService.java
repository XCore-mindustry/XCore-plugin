package org.xcore.plugin.gamemode.pvp.guide;

import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.EventType;
import mindustry.gen.Player;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.ScheduledTasks;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.event.EventSubscriptions;
import org.xcore.plugin.gamemode.pvp.MiniPvP;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPGuideUiController.GuideModel;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPGuideUiController.Tab;
import org.xcore.plugin.gamemode.pvp.guide.MiniPvPModeFacts.TeamCount;
import org.xcore.plugin.gamemode.pvp.rating.MiniPvPLadder;
import org.xcore.plugin.rating.ladder.Ladder;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.startup.ShutdownOrder;
import org.xcore.plugin.ui.MenuService;

import java.util.List;

/**
 * Opens the MiniPvP guide: on request, and by itself for players who are new to the mode.
 *
 * <p>Regulars are not interrupted on every join. Someone with a few rated rounds behind them
 * gets one chat line naming the command instead.
 */
@Singleton
public class MiniPvPGuideService {
    /** Rated rounds after which a player is expected to know the mode. */
    static final int NEWCOMER_MATCHES = 3;

    /**
     * The session is published a moment after {@code PlayerJoin}, once the player's data is
     * loaded, and announced with no event; the guide waits for it this many times.
     */
    private static final int SESSION_ATTEMPTS = 20;
    private static final float SESSION_RETRY_SECONDS = 0.5f;

    private final EventSubscriptions subscriptions = new EventSubscriptions();
    private final ScheduledTasks tasks = new ScheduledTasks(MiniPvPGuideService.class);

    private final TomlXcoreConfig config;
    private final MenuService menuService;
    private final SessionService sessions;
    private final MiniPvP miniPvP;
    private final MiniPvPLadder ladder;
    private final Async async;

    @Inject
    public MiniPvPGuideService(TomlXcoreConfig config, MenuService menuService, SessionService sessions,
                               MiniPvP miniPvP, MiniPvPLadder ladder, Async async) {
        this.config = config;
        this.menuService = menuService;
        this.sessions = sessions;
        this.miniPvP = miniPvP;
        this.ladder = ladder;
        this.async = async;
    }

    @PostConstruct
    public void init() {
        if (!hostsMode()) return;
        subscriptions.on(EventType.PlayerJoin.class, event -> onJoin(event.player));
    }

    @PreDestroy(priority = ShutdownOrder.STOP_PRODUCERS)
    public void shutdown() {
        subscriptions.close();
        tasks.close();
    }

    /** Whether this server runs MiniPvP; the guide describes no other mode. */
    public boolean hostsMode() {
        return "mini-pvp".equals(config.server.name);
    }

    /** Opens the guide for the player of {@code session}; call on the game thread. */
    public boolean open(Session session) {
        if (session == null || session.player == null || session.player.con == null) return false;
        session.clear();
        var controller = new MiniPvPGuideUiController(session.locale());
        var model = new GuideModel(Tab.ROUND, currentFacts());
        // Filling the screen is what lets the cards scroll on a screen shorter than the guide.
        return menuService.openUi(session, controller, model, true) != null;
    }

    private MiniPvPModeFacts currentFacts() {
        List<TeamCount> teams = miniPvP.aliveTeams().entrySet().stream()
                .map(entry -> new TeamCount(entry.getKey().id, entry.getValue()))
                .toList();
        return MiniPvPModeFacts.current(teams, miniPvP.roundSeconds(), ladder.policy());
    }

    private void onJoin(Player player) {
        if (player == null || player.con == null) return;
        String uuid = player.uuid();
        async.forPlayer(player, () -> isNewcomer(ladder.ladder(), uuid),
                (joined, newcomer) -> whenSessionReady(joined, SESSION_ATTEMPTS, newcomer));
    }

    /**
     * Blocking. Whether the player has yet to play enough rated rounds to know the mode. Past
     * seasons count: a new season empties the standing, not what the player has learned.
     */
    static boolean isNewcomer(Ladder ladder, String uuid) {
        int matches = ladder.standing(uuid).matches();
        if (matches >= NEWCOMER_MATCHES) return false;
        for (var past : ladder.history(uuid, NEWCOMER_MATCHES)) {
            matches += past.matches();
        }
        return matches < NEWCOMER_MATCHES;
    }

    private void whenSessionReady(Player player, int attemptsLeft, boolean newcomer) {
        if (!Async.isPlayerOnline(player)) return;
        Session session = sessions.get(player);
        if (session == null || session.locale() == null) {
            if (attemptsLeft > 0) {
                tasks.after(SESSION_RETRY_SECONDS, () -> whenSessionReady(player, attemptsLeft - 1, newcomer));
            }
            return;
        }
        greet(session, newcomer);
    }

    void greet(Session session, boolean newcomer) {
        // A menu the player opened in the meantime, or one the join opened, is theirs; do not replace it.
        if (newcomer && !menuService.isMenuOpen(session) && open(session)) return;
        session.locale().send("pvp-guide-join-hint");
    }
}
