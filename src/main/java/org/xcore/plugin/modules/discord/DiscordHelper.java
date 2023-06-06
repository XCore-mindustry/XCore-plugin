package org.xcore.plugin.modules.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;

public class DiscordHelper {
    public static boolean hasRole(Member member, long roleId) {
        return member.getRoleIds().contains(Snowflake.of(roleId));
    }
}