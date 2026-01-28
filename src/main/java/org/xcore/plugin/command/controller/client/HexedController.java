package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.database.DatabaseService;
import org.xcore.plugin.gamemode.hexed.HexedRanks;
import org.xcore.plugin.gamemode.hexed.MiniHexedService;
import org.xcore.plugin.gamemode.hexed.UnitState;
import org.xcore.plugin.service.FindService;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class HexedController {

    private final DatabaseService database;
    private final MiniHexedService hexedService;
    private final FindService findService;

    @Inject
    public HexedController(DatabaseService database, MiniHexedService hexedService, FindService findService) {
        this.database = database;
        this.hexedService = hexedService;
        this.findService = findService;
    }

    @Command(name = "spectate")
    public void spectate(ClientContext ctx) {
        hexedService.killTeam(ctx.player().team());
        ctx.send("commands-spectate-success", args());
    }

    @Command(name = "rank", params = "[player...]")
    public void rank(ClientContext ctx) {
        Player target = ctx.args().length > 0
                ? findService.player(ctx.arg(0))
                : ctx.player();

        if (target == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        var data = database.getCached(target.uuid());
        var rank = data.hexedRank();

        ctx.player().sendMessage(ctx.format("commands-rank-content", args(
                "nickname", target.name,
                "rankTag", rank.tag,
                "rankName", ctx.format("hexed-ranks-" + rank.name(), args()),
                "points", data.hexedPoints,
                "requiredPoints", rank.next != null ? rank.next.requirements.wins() : 0
        )));
    }

    @Command(name = "ranks")
    public void ranks(ClientContext ctx) {
        StringBuilder sb = new StringBuilder();
        for (HexedRanks.HexedRank r : HexedRanks.HexedRank.values()) {
            sb.append(ctx.format("commands-ranks-content", args(
                    "rankTag", r.tag,
                    "rankName", ctx.format("hexed-ranks-" + r.name(), args()),
                    "requiredPoints", r.requirements != null ? r.requirements.wins() : 0
            ))).append("\n");
        }
        sb.append(ctx.format("commands-ranks-footer", args()));
        Call.infoMessage(ctx.player().con, sb.toString());
    }

    @Command(name = "ai", params = "<attack/idle>")
    public void ai(ClientContext ctx) {
        var member = hexedService.members.get(ctx.player().uuid());

        if (ctx.player().team() == Team.derelict) {
            ctx.send("error-spectator", args());
            return;
        }

        if (ctx.arg(0).startsWith("a")) {
            member.setUnitState(UnitState.ATTACK);
        } else if (ctx.arg(0).startsWith("i")) {
            member.setUnitState(UnitState.IDLE);
        } else {
            ctx.send("commands-ai-usage", args());
            return;
        }
        ctx.send("success", args());
    }
}