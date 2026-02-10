package org.xcore.plugin.command.controller.client;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Player;
import org.incendo.cloud.annotation.specifier.Greedy;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.cloud.annotation.PlayTimeLimit;
import org.xcore.plugin.cloud.annotation.RequiresPlayTime;
import org.xcore.plugin.command.controller.CloudClientController;
import org.xcore.plugin.vote.*;
import org.xcore.plugin.common.TextUtils;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class VoteController implements CloudClientController {

    private final VoteService voteService;
    private final VoteKickFactory voteKickFactory;

    @Inject
    public VoteController(VoteService voteService,
                          VoteKickFactory voteKickFactory) {
        this.voteService = voteService;
        this.voteKickFactory = voteKickFactory;
    }

    @RequiresPlayTime(PlayTimeLimit.VOTE_KICK)
    @Command("votekick <target> <reason>")
    public void votekick(XCoreSender sender, @Argument("target") Player target, @Argument("reason") @Greedy String reason) {

        if (voteService.getCurrentVoteKick() != null) {
            sender.send("error-vote-in-progress", args());
            return;
        }

        if (target.admin) {
            sender.player().kick(sender.format("error-player-admin", args()), 300000);
            return;
        }

        if (target.team() != sender.player().team()) {
            sender.send("error-player-not-teammate", args());
            return;
        }

        VoteKick kick = voteKickFactory.create(sender.player(), target, reason);
        voteService.startVote(kick);
        kick.vote(sender.player(), 1);
    }

    @Command("vote <choice>")
    public void vote(XCoreSender sender, @Argument("choice") String choice) {

        VoteSession currentSession = voteService.getCurrentSession();
        if (currentSession == null) {
            sender.send("error-no-voting", args());
            return;
        }

        String c = TextUtils.stripFooCharacters(choice.toLowerCase());

        if (c.equals("c")) {
            if (!sender.player().admin) {
                sender.send("error-access-denied", args());
                return;
            }
            currentSession.cancelByAdmin(sender.player());
            return;
        }

        if (currentSession.voted.containsKey(sender.player().id)) {
            sender.send("error-already-voted", args());
            return;
        }

        if (currentSession instanceof VoteKick voteKick && voteKick.target == sender.player()) {
            sender.send("error-vote-yourself", args());
            return;
        }

        VoteChoice sign = VoteChoice.parse(c);
        if (!sign.isValid()) {
            sender.send("commands-vote-vote-with", args());
            return;
        }

        currentSession.vote(sender.player(), sign.sign());
    }
}
