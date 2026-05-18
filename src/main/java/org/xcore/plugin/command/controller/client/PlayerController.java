package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.plugin.ui.menu.TopMenu;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerController implements CloudClientController {

    private final PlayerDataRepository playerDataRepository;
    private final SessionService sessionService;
    private final ObserverService observerService;
    private final PlayerMenu menu;
    private final TopMenu topMenu;

    @Inject
    public PlayerController(PlayerDataRepository playerDataRepository,
                            SessionService sessionService,
                            ObserverService observerService,
                            PlayerMenu menu,
                            TopMenu topMenu) {
        this.playerDataRepository = playerDataRepository;
        this.sessionService = sessionService;
        this.observerService = observerService;
        this.menu = menu;
        this.topMenu = topMenu;
    }

    @Command("player|stats|player-statistics [id]")
    public void player(XCoreSender sender, @Argument("id") @Default("-1") int id) {
        var session = sessionService.get(sender.player().uuid());
        PlayerData data = id == -1
                ? (session != null ? session.data : sessionService.getOrLoadFromDb(sender.player().uuid()))
                : sessionService.getOrLoadFromDb(id);

        if (data == null) {
            return;
        }

        menu.player(menu.getUuid(sender), data);
    }

    @Command("settings [id]")
    public void settings(XCoreSender sender, @Argument("id") @Default("-1") int id) {
        var session = sessionService.get(sender.player().uuid());
        PlayerData data = id == -1
                ? (session != null ? session.data : sessionService.getOrLoadFromDb(sender.player().uuid()))
                : sessionService.getOrLoadFromDb(id);

        if (data == null) {
            return;
        }

        menu.settings(menu.getUuid(sender), data);
    }

    @Command("observer")
    public void observer(XCoreSender sender) {
        var player = sender.player();
        var session = sessionService.get(player.uuid());

        if (observerService.isObserving(session)) {
            observerService.exit(session);

            if (session != null) {
                session.locale().send("commands-observer-exit-success");
            }
            return;
        }

        observerService.enter(player);

        if (session != null) {
            session.locale().send("commands-observer-success");
        }
    }

    @Permission("admin")
    @Command("set-team [id] [pid]")
    public void setTeam(XCoreSender sender, @Argument("id") @Default("-1") int id, @Argument("pid") @Default("-1") int pid) {
        Team team = id == -1 ? sender.player().team() : Team.get(id);

        Session targetSession;
        if (pid == -1) {
            targetSession = sessionService.get(sender.player().uuid());
        } else {
            var dbPlayer = sessionService.getOrLoadFromDb(pid);
            targetSession = (dbPlayer != null) ? sessionService.get(dbPlayer.uuid) : null;
        }

        if (targetSession == null || targetSession.player == null) {
            return;
        }

        if (observerService.isObserving(targetSession) && !observerService.isObserverTeam(team)) {
            observerService.resetObserverState(targetSession.data.uuid);
        }

        targetSession.player.clearUnit();
        targetSession.player.team(team);
    }


    @Command("lb")
    public void leaderboard(XCoreSender sender) {
        var session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) {
            return;
        }

        session.data.leaderboard = !session.data.leaderboard;

        sender.send("commands-lb-success", args(
                "leaderboardEnabled", String.valueOf(session.data.leaderboard)
        ));

        sessionService.updateLeaderboard(session, session.data.leaderboard);
    }

    @Command("top")
    public void top(XCoreSender sender) {
        topMenu.top(sender.player().uuid());
    }
}
