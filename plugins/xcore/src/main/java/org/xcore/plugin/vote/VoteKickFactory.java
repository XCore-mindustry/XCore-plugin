package org.xcore.plugin.vote;

import mindustry.gen.Player;

public interface VoteKickFactory {
    VoteKick create(Player starter, Player target, String reason);
}
