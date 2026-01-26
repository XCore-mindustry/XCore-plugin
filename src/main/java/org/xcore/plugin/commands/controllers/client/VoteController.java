package org.xcore.plugin.commands.controllers.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.infra.commands.annotation.Command;
import org.xcore.plugin.infra.commands.annotation.MinPlayTime;
import org.xcore.plugin.infra.commands.context.ClientContext;
import org.xcore.plugin.modules.votes.VoteFactory;
import org.xcore.plugin.modules.votes.VoteKick;
import org.xcore.plugin.modules.votes.VoteService;
import org.xcore.plugin.utils.FindService;
import org.xcore.plugin.utils.Utils;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class VoteController {
    private final FindService findService;
    private final VoteService voteService;
    private final VoteFactory voteFactory;

    @Inject
    public VoteController(FindService findService, VoteService voteService, VoteFactory voteFactory) {
        this.findService = findService;
        this.voteService = voteService;
        this.voteFactory = voteFactory;
    }

    @MinPlayTime(minutes = 60, errorKey = "error-votekick-total-playtime")
    @Command(name = "votekick", params = "<id/name> <reason...>")
    public void votekick(ClientContext ctx) {
        if (voteService.getCurrentVoteKick() != null) {
            ctx.send("error-vote-in-progress", args());
            return;
        }

        Player found = findService.player(ctx.arg(0));
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

        VoteKick kick = voteFactory.createKick(ctx.player(), found, ctx.args()[1]);
        voteService.startVote(kick);
        kick.vote(ctx.player(), 1);
    }

    @Command(name = "vote", params = "<y/n/c>")
    public void vote(ClientContext ctx) {
        VoteKick current = voteService.getCurrentVoteKick();

        if (current == null) {
            ctx.send("error-no-voting", args());
            return;
        }

        String choice = Utils.stripFooCharacters(ctx.arg(0).toLowerCase());

        if (choice.equals("c")) {
            if (!ctx.player().admin) {
                ctx.send("error-access-denied", args());
            } else {
                current.cancelByAdmin(ctx.player());
            }
            return;
        }

        if (current.voted.containsKey(ctx.player().id)) {
            ctx.send("error-already-voted", args());
            return;
        }

        if (current.target == ctx.player()) {
            ctx.send("error-vote-yourself", args());
            return;
        }

        int sign = Utils.voteChoice(choice);
        if (sign == 0) {
            ctx.send("commands-vote-vote-with", args());
            return;
        }

        current.vote(ctx.player(), sign);
    }
}