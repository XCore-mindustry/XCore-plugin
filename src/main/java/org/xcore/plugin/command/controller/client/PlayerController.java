package org.xcore.plugin.command.controller.client;

import arc.struct.Seq;
import arc.util.Log;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.config.Config;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.ui.menu.PlayerMenu;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class PlayerController implements CloudClientController {

    private final PlayerDataRepository playerDataRepository;
    private final SessionService sessionService;
    private final Config config;
    private final PlayerMenu menu;

    @Inject
    public PlayerController(PlayerDataRepository playerDataRepository,
                            SessionService sessionService,
                            Config config,
                            PlayerMenu menu) {
        this.playerDataRepository = playerDataRepository;
        this.sessionService = sessionService;
        this.config = config;
        this.menu = menu;
    }

    @Command("player|stats|player-statistics [id]")
    public void player(XCoreSender sender, @Argument("id") @Default("-1") int id) {
        PlayerData data = id == -1
                ? sessionService.get(sender.player().uuid()).data
                : sessionService.getOrLoadFromDb(id);

        menu.player(menu.getUuid(sender), data);
    }

    @Command("settings [id]")
    public void settings(XCoreSender sender, @Argument("id") @Default("-1") int id) {
        PlayerData data = id == -1
                ? sessionService.get(sender.player().uuid()).data
                : sessionService.getOrLoadFromDb(id);

        menu.settings(menu.getUuid(sender), data);
    }

    @Command("observer")
    public void observer(XCoreSender sender) {
        var player = sender.player();

        player.clearUnit();
        player.team(Team.derelict);

        sessionService.get(sender.player().uuid()).locale().send("commands-spectate-success");
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

        targetSession.player.clearUnit();
        targetSession.player.team(team);
    }


    @Command("lb")
    public void leaderboard(XCoreSender sender) {
        var session = sessionService.get(sender.player().uuid());
        session.data.leaderboard = !session.data.leaderboard;

        sender.send("commands-lb-success", args(
                "leaderboardEnabled", String.valueOf(session.data.leaderboard)
        ));

        session.save();
    }

    @Command("top")
    public void top(XCoreSender sender) {
        boolean isHexed = config.isMiniHexed();

        Seq<PlayerData> leaders = isHexed
                ? playerDataRepository.findLeaders("hexed_rank", "hexed_points")
                : playerDataRepository.findLeaders("pvp_rating");

        if (leaders.isEmpty()) {
            sender.send("empty", args());
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < leaders.size; i++) {
            var d = leaders.get(i);
            String key = isHexed ? "commands-top-hexed-content" : "commands-top-pvp-content";

            builder.append(sender.format(key, args(
                    "index", i + 1,
                    "nickname", d.nickname,
                    "rating", d.pvpRating,
                    "rankName", sender.format("hexed-ranks-" + d.hexedRank().name(), args()),
                    "points", d.hexedPoints
            ))).append("\n");
        }

        sender.player().sendMessage(builder.toString());
    }
}