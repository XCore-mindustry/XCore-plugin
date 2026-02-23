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
import org.xcore.plugin.localization.Localization;
import org.xcore.plugin.model.PlayerData;
import org.xcore.plugin.session.Session;
import org.xcore.plugin.session.SessionService;
import org.xcore.plugin.vote.*;
import org.xcore.plugin.common.TextUtils;

import static com.ospx.flubundle.Bundle.args;

@Singleton
public class VoteController implements CloudClientController {

    private final SessionService sessionService;
    private final VoteService voteService;
    private final VoteKickFactory voteKickFactory;

    @Inject
    public VoteController(SessionService sessionService, VoteService voteService,
                          VoteKickFactory voteKickFactory) {
        this.sessionService = sessionService;
        this.voteService = voteService;
        this.voteKickFactory = voteKickFactory;
    }

    @RequiresPlayTime(PlayTimeLimit.VOTE_KICK)
    @Command("votekick <target> <reason>")
    public void votekick(XCoreSender sender, @Argument("target") Player target, @Argument("reason") @Greedy String reason) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        Localization local = session.locale();

        if (voteService.getCurrentVoteKick() != null) {
            local.send("error-vote-in-progress", args());
            return;
        }

        if (target.admin) {
            session.player.kick(local.format("error-player-admin", args()), 300000);
            return;
        }

        if (target.team() != session.player.team()) {
            local.send("error-player-not-teammate", args());
            return;
        }

        VoteKick kick = voteKickFactory.create(session.player, target, reason);
        voteService.startVote(kick);
        kick.vote(sender.player(), 1);
    }

    @Command("vote <choice>")
    public void vote(XCoreSender sender, @Argument("choice") String choice) {
        Session session = sessionService.get(sender.player().uuid());
        if (session == null || session.data == null) return;
        Localization local = session.locale();

        VoteSession currentSession = voteService.getCurrentSession();
        if (currentSession == null) {
            local.send("error-no-voting", args());
            return;
        }

        String c = TextUtils.stripFooCharacters(choice.toLowerCase());

        if (c.equals("c")) {
            if (!session.player.admin) {
                local.send("error-access-denied", args());
                return;
            }
            currentSession.cancelByAdmin(session.player);
            return;
        }

        if (currentSession.voted.containsKey(session.player.id)) {
            local.send("error-already-voted", args());
            return;
        }

        if (currentSession instanceof VoteKick voteKick && voteKick.target == session.player) {
            local.send("error-vote-yourself", args());
            return;
        }

        VoteChoice sign = VoteChoice.parse(c);
        if (!sign.isValid()) {
            local.send("commands-vote-vote-with", args());
            return;
        }

        currentSession.vote(session.player, sign.sign());
    }
}
