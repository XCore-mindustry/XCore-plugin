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
import org.xcore.plugin.service.PlayerSessionService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class HexedController implements CloudClientController {

    private final PlayerSessionService playerSessionService;
    private final MiniHexedService hexedService;

    @Inject
    public HexedController(PlayerSessionService playerSessionService,
                           MiniHexedService hexedService
    ) {
        this.playerSessionService = playerSessionService;
        this.hexedService = hexedService;
    }

    @Command("spectate")
    public void spectate(XCoreSender sender) {
        hexedService.killTeam(sender.player().team());
        sender.send("commands-spectate-success", args());
    }

    @Command("rank")
    public void rankSelf(XCoreSender sender) {
        rank(sender, sender.player());
    }

    @Command("rank <player>")
    public void rank(XCoreSender sender,
                     @Argument("player") Player target) {
        if (target == null) {
            sender.send("error-player-not-found", args());
            return;
        }

        var data = playerSessionService.get(target.uuid());
        var rank = data.hexedRank();

        sender.player().sendMessage(sender.format("commands-rank-content", args(
                "nickname", target.name,
                "rankTag", rank.tag,
                "rankName", sender.format("hexed-ranks-" + rank.name(), args()),
                "points", data.hexedPoints,
                "requiredPoints", rank.next != null ? rank.next.requirements.wins() : 0
        )));
    }

    @Command("ranks")
    public void ranks(XCoreSender sender) {
        StringBuilder sb = new StringBuilder();
        for (HexedRanks.HexedRank r : HexedRanks.HexedRank.values()) {
            sb.append(sender.format("commands-ranks-content", args(
                    "rankTag", r.tag,
                    "rankName", sender.format("hexed-ranks-" + r.name(), args()),
                    "requiredPoints", r.requirements != null ? r.requirements.wins() : 0
            ))).append("\n");
        }
        sb.append(sender.format("commands-ranks-footer", args()));
        Call.infoMessage(sender.player().con, sb.toString());
    }

    @Command("ai <state>")
    public void ai(XCoreSender sender,
                   @Argument("state") String state) {

        var member = hexedService.members.get(sender.player().uuid());

        if (sender.player().team() == Team.derelict) {
            sender.send("error-spectator", args());
            return;
        }

        if (state.startsWith("a")) {
            member.setUnitState(UnitState.ATTACK);
        } else if (state.startsWith("i")) {
            member.setUnitState(UnitState.IDLE);
        } else {
            sender.send("commands-ai-usage", args());
            return;
        }
        sender.send("success", args());
    }


}
