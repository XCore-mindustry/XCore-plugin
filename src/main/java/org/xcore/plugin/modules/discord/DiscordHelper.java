package org.xcore.plugin.modules.discord;

import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;

import org.xcore.plugin.utils.models.PlayerData;

import arc.util.Strings;

import java.time.Instant;

import static org.xcore.plugin.PluginVars.globalConfig;

public class DiscordHelper {
    public static boolean hasRole(Member member, long roleId) {
        return member.getRoleIds().contains(Snowflake.of(roleId));
    }

    public static boolean buttonFilter(ButtonInteractionEvent event, MessageContext context, Message message) {
        var member = event.getInteraction().getMember().orElseThrow();
        return context.member().getId().equals(member.getId()) && message.getId().equals(event.getMessageId());
    }

    public static boolean checkId(MessageContext context, int id) {
        if (id < 0) {
            context.error("Invalid number", "'player-id' must be a positive number!").subscribe();
            return true;
        }

        return false;
    }

    public static boolean checkPeriod(MessageContext context, Instant period) {
        if (period == null) {
            context.error(
                "Wrong period format",
                "The period must be a number or in the format \"number<m/h/d/y> (minutes/hours/days/years)"
            ).subscribe();

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

    public static boolean notFound(MessageContext context, String server) {
        if (server == null) {
            context.error(
                "Invalid server name",
                "Server with provided name not found!\nServers: @",
                Strings.join(", ", globalConfig.servers.keys())
            ).subscribe();

            return true;
        }

        return false;
    }

    public static void noResponse(MessageContext context) {
        context.error(
            "Internal Error",
            "The server did not respond. Perhaps the server is down or an error has occurred."
        ).subscribe();
    }
}