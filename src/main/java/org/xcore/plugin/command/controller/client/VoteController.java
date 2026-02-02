package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.xcore.plugin.command.core.annotation.Command;
import org.xcore.plugin.command.core.annotation.MinPlayTime;
import org.xcore.plugin.command.core.annotation.PlayTimeLimit;
import org.xcore.plugin.command.core.context.ClientContext;
import org.xcore.plugin.vote.VoteChoice;
import org.xcore.plugin.vote.VoteKick;
import org.xcore.plugin.vote.VoteKickFactory;
import org.xcore.plugin.vote.VoteService;
import org.xcore.plugin.service.FindService;
import org.xcore.plugin.common.TextUtils;

import static com.ospx.flubundle.Bundle.args;

import org.xcore.plugin.command.core.ClientController;

@Singleton
public class VoteController implements ClientController {
    private final FindService findService;
    private final VoteService voteService;
    private final VoteKickFactory voteKickFactory;

    @Inject
    public VoteController(FindService findService, VoteService voteService, VoteKickFactory voteKickFactory) {
        this.findService = findService;
        this.voteService = voteService;
        this.voteKickFactory = voteKickFactory;
    }

    @Override
    public int priority() {
        return 70;
    }

    @MinPlayTime(value = PlayTimeLimit.VOTE_KICK, errorKey = "error-votekick-total-playtime")
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

        VoteKick kick = voteKickFactory.create(ctx.player(), found, ctx.args()[1]);
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

        String choice = TextUtils.stripFooCharacters(ctx.arg(0).toLowerCase());

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

        VoteChoice sign = VoteChoice.parse(choice);
        if (!sign.isValid()) {
            ctx.send("commands-vote-vote-with", args());
            return;
        }

        current.vote(ctx.player(), sign.sign());
    }
}