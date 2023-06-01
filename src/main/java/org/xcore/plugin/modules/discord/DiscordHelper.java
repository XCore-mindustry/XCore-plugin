package org.xcore.plugin.modules.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;

import java.util.Optional;

public class DiscordHelper {
    public static boolean hasRole(Optional<Member> member, long roleId) {
        return member.map(m -> m.getRoleIds().contains(Snowflake.of(roleId))).orElse(false);
    }
}