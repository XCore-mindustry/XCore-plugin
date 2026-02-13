package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.gamemode.hexed.MiniHexedService;
import org.xcore.plugin.gamemode.hexed.UnitState;
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class HexedController implements CloudClientController {

    private final SessionService sessionService;
    private final MiniHexedService hexedService;

    @Inject
    public HexedController(SessionService sessionService,
                           MiniHexedService hexedService
    ) {
        this.sessionService = sessionService;
        this.hexedService = hexedService;
    }

    @Command("spectate")
    public void spectate(XCoreSender sender) {
        Session session = sessionService.get(sender.player().uuid());
        hexedService.killTeam(session.player.team());
        session.locale().send("commands-spectate-success", args());
    }

    @Command("rank")
    public void rankSelf(XCoreSender sender) {
        rank(sender, sender.player());
    }

    @Command("rank <player>")
    public void rank(XCoreSender sender,
                     @Argument("player") Player target) {
        Session s = sessionService.get(sender.player().uuid());
        Localization local = s.locale();

        if (target == null) {
            local.send("error-player-not-found", args());
            return;
        }

        Session session = sessionService.get(target.uuid());
        PlayerData data = session.data;
        var rank = data.hexedRank();

        s.player.sendMessage(local.format("commands-rank-content", args(
                "nickname", target.name,
                "rankTag", rank.tag,
                "rankName", local.format("hexed-ranks-" + rank.name(), args()),
                "points", data.hexedPoints,
                "requiredPoints", rank.next != null ? rank.next.requirements.wins() : 0
        )));
    }

    @Command("ranks")
    public void ranks(XCoreSender sender) {
        Session session = sessionService.get(sender.player().uuid());
        Localization local = session.locale();

        StringBuilder sb = new StringBuilder();
        for (HexedRanks.HexedRank r : HexedRanks.HexedRank.values()) {
            sb.append(local.format("commands-ranks-content", args(
                    "rankTag", r.tag,
                    "rankName", local.format("hexed-ranks-" + r.name(), args()),
                    "requiredPoints", r.requirements != null ? r.requirements.wins() : 0
            ))).append("\n");
        }
        sb.append(local.format("commands-ranks-footer", args()));
        Call.infoMessage(session.player.con, sb.toString());
    }

    @Command("ai <state>")
    public void ai(XCoreSender sender,
                   @Argument("state") String state) {

        Session session = sessionService.get(sender.player().uuid());
        Localization local = session.locale();

        var member = hexedService.members.get(session.data.uuid);

        if (session.player.team() == Team.derelict) {
            local.send("error-spectator", args());
            return;
        }

        if (state.startsWith("a")) {
            member.setUnitState(UnitState.ATTACK);
        } else if (state.startsWith("i")) {
            member.setUnitState(UnitState.IDLE);
        } else {
            local.send("commands-ai-usage", args());
            return;
        }
        local.send("success", args());
    }
}
