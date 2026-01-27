package org.xcore.plugin.modules.votes;

import mindustry.gen.Player;

public interface VoteKickFactory {
    VoteKick create(Player starter, Player target, String reason);
}
