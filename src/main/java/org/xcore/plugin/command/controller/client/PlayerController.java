package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.jspecify.annotations.Nullable;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.model.PlayerPids;
import org.xcore.plugin.permission.PermissionNodes;
import org.xcore.plugin.session.ObserverService;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.PlayerMenu;
import org.xcore.plugin.ui.menu.TopMenu;

import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerController implements CloudClientController {

    private final SessionService sessionService;
    private final ObserverService observerService;
    private final PlayerMenu menu;
    private final TopMenu topMenu;
    private final Async async;

    @Inject
    public PlayerController(SessionService sessionService,
                            ObserverService observerService,
                            PlayerMenu menu,
                            TopMenu topMenu,
                            Async async) {
        this.sessionService = sessionService;
        this.observerService = observerService;
        this.menu = menu;
        this.topMenu = topMenu;
        this.async = async;
    }

    public PlayerController(SessionService sessionService,
                            ObserverService observerService,
                            PlayerMenu menu,
                            TopMenu topMenu) {
        this(sessionService, observerService, menu, topMenu, null);
    }

    @Command("player|stats|me|player-statistics [target]")
    public void player(XCoreSender sender, @Argument("target") String target) {
        openForTarget(sender.player(), target, (p, data) -> menu.player(p.uuid(), data));
    }

    @Command("settings [target]")
    public void settings(XCoreSender sender, @Argument("target") String target) {
        openForTarget(sender.player(), target, (p, data) -> menu.settings(p.uuid(), data));
    }

    @Command("players")
    public void players(XCoreSender sender) {
        if (sender == null || sender.player() == null) return;
        menu.players(sender.player().uuid(), 1);
    }

    private void openForTarget(Player player, String target, BiConsumer<Player, PlayerData> openAction) {
        if (player == null) return;

        // Якщо аргумент порожній — відкриваємо себе
        if (target == null || target.isBlank()) {
            Session session = sessionService.get(player.uuid());
            if (session != null && session.data != null) {
                openAction.accept(player, session.data);
                return;
            }
            fetchAndOpen(player, sessionService.getOrLoadFromDbAsync(player.uuid()), openAction);
            return;
        }

        Integer pid = PlayerPids.parse(target);
        if (pid != null) {
            Session targetOnline = sessionService.findOnlineByPid(pid);
            if (targetOnline != null && targetOnline.data != null) {
                openAction.accept(player, targetOnline.data);
                return;
            }
        }

        String userCandidate = target.startsWith("@") ? target.substring(1) : target;
        Session targetOnlineUser = sessionService.findOnlineByUsername(userCandidate);
        if (targetOnlineUser != null && targetOnlineUser.data != null) {
            openAction.accept(player, targetOnlineUser.data);
            return;
        }

        fetchAndOpen(player, sessionService.resolvePlayerDataAsync(target), openAction);
    }

    private void fetchAndOpen(Player player, CompletionStage<PlayerData> stage, BiConsumer<Player, PlayerData> openAction) {
        if (player == null || stage == null) return;

        if (async != null) {
            async.onMainForPlayer(player, stage, (p, data) -> {
                if (data != null) {
                    openAction.accept(p, data);
                } else {
                    Session s = sessionService.get(p.uuid());
                    if (s != null && s.locale() != null) {
                        s.locale().send("error-player-not-found");
                    }
                }
            });
            return;
        }

        stage.whenComplete((data, error) -> {
            if (error == null && data != null) {
                openAction.accept(player, data);
            } else {
                Session s = sessionService.get(player.uuid());
                if (s != null && s.locale() != null) {
                    s.locale().send("error-player-not-found");
                }
            }
        });
    }

    @Command("observer|spectate")
    public void observer(XCoreSender sender) {
        var player = sender.player();
        var session = resolveSession(sender, sessionService);

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

    @Permission(PermissionNodes.ADMIN_SET_TEAM)
    @Command("set-team [id] [pid]")
    public void setTeam(XCoreSender sender, @Argument("id") @Default("-1") int id, @Nullable @Argument("pid") Integer pid) {
        Team team = id == -1 ? sender.player().team() : Team.get(id);

        Session targetSession;
        if (pid == null) {
            targetSession = sessionService.get(sender.player().uuid());
        } else {
            targetSession = sessionService.findOnlineByPid(pid);
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
