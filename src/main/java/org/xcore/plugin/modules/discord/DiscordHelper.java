package org.xcore.plugin.modules.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import org.xcore.plugin.utils.models.PlayerData;

import java.time.Instant;

public class DiscordHelper {
    public static boolean hasRole(Member member, long roleId) {
        return member.getRoleIds().contains(Snowflake.of(roleId));
    }

    public static boolean noRole(MessageContext context, long roleId) {
        if (!hasRole(context.member(), roleId)) {
            context.error("Missing permissions", "You must be at least @ to use this command.", "<@&" + roleId + ">")
                    .subscribe();
            return true;
        }

        return false;
    }

    public static boolean checkId(MessageContext context, int id) {
        if (id < 0) {
            context.error("Invalid number", "'player-id' must be a positive number!")
                    .subscribe();
            return true;
        }

        return false;
    }

    public static boolean checkPeriod(MessageContext context, Instant period) {
        if (period == null) {
            context.error("Wrong period format", "The period must be a number or in the format \"number<m/h/d/y> (minutes/hours/days/years)")
                    .subscribe();
            return true;
        }

        return false;
    }

    public static boolean notFound(MessageContext context, PlayerData data) {
        if (data == null) {
            context.error("Player not found", "Check if the input is correct.").subscribe();
            return true;
        }

        return false;
    }
}