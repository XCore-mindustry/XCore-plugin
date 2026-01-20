package org.xcore.plugin.commands.controllers;

import mindustry.gen.Player;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.annotation.MinPlayTime;
import org.xcore.plugin.infra.commands.context.CommandContext;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.utils.Find;
import org.xcore.plugin.utils.Utils;

import static com.ospx.flubundle.Bundle.args;
import static org.xcore.plugin.PluginVars.voteKick;

@SuppressWarnings("unused")
public class VoteController {

    @MinPlayTime(minutes = 60, errorKey = "error-votekick-total-playtime")
    @Command(name = "votekick", params = "<id/name> <reason...>")
    public void votekick(CommandContext<Player> ctx) {
        if (voteKick != null) {
            ctx.send("error-vote-in-progress", args());
            return;
        }

        Player found = Find.player(ctx.arg(0));
        if (found == null) {
            ctx.send("error-player-not-found", args());
            return;
        }

        if (found.admin) {
            ctx.player().kick(ctx.format("error-player-admin", args()), 300000);
            return;
        }

        if (found.team() != ctx.player().team()) {
            ctx.send("error-player-not-teammate", args());
            return;
        }

        voteKick = new VoteKick(ctx.player(), found, ctx.args()[1]);
        voteKick.vote(ctx.player(), 1);
    }

    @Command(name = "vote", params = "<y/n/c>")
    public void vote(CommandContext<Player> ctx) {
        if (voteKick == null) {
            ctx.send("error-no-voting", args());
            return;
        }

        String choice = Utils.stripFooCharacters(ctx.arg(0).toLowerCase());

        if (choice.equals("c")) {
            if (!ctx.player().admin) {
                ctx.send("error-access-denied", args());
            } else {
                voteKick.cancelByAdmin(ctx.player());
            }
            return;
        }

        if (voteKick.voted.containsKey(ctx.player().id)) {
            ctx.send("error-already-voted", args());
            return;
        }

        if (voteKick.target == ctx.player()) {
            ctx.send("error-vote-yourself", args());
            return;
        }

        int sign = Utils.voteChoice(choice);
        if (sign == 0) {
            ctx.send("commands-vote-vote-with", args());
            return;
        }

        voteKick.vote(ctx.player(), sign);
    }
}