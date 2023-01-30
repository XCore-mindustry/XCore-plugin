package org.xcore.plugin.modules.discord;

import arc.util.Strings;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.xcore.plugin.XcorePlugin;

import java.awt.*;

import static net.dv8tion.jda.api.requests.GatewayIntent.*;
import static net.dv8tion.jda.api.utils.MemberCachePolicy.OWNER;
import static net.dv8tion.jda.api.utils.MemberCachePolicy.VOICE;
import static org.xcore.plugin.modules.ServersConfig.servers;
import static org.xcore.plugin.PluginVars.*;

public class Bot {
    public static JDA jda;
    public static TextChannel bansChannel;
    public static Role adminRole;

    public static boolean isConnected = false;
    public static void connect() {
        try {
            jda = JDABuilder.createLight(config.discordBotToken)
                    .disableCache(CacheFlag.ACTIVITY)
                    .setMemberCachePolicy(VOICE.or(OWNER))
                    .setChunkingFilter(ChunkingFilter.NONE)
                    .disableIntents(GUILD_MESSAGE_TYPING, GUILD_PRESENCES)
                    .setLargeThreshold(50)
                    .enableIntents(GUILD_MEMBERS, MESSAGE_CONTENT)
                    .addEventListeners(new DiscordListeners())
                    .build()
                    .awaitReady();

            bansChannel = jda.getTextChannelById(config.discordBansChannelId);
            adminRole = jda.getRoleById(config.discordAdminRoleId);
            isConnected = true;
        } catch (Exception e) {
            XcorePlugin.err("Error while connecting to discord: ");
            e.printStackTrace();
        }
    }
    public static TextChannel getServerLogChannel(String server) {
        return jda.getTextChannelById(servers.get(server));
    }

    public static void sendMessageEventMessage(String playerName, String message) {
        sendMessageEventMessage(playerName, message, config.server);
    }

    public static void sendMessageEventMessage(String playerName, String message, String server) {
        if (!isConnected) return;
        getServerLogChannel(server).sendMessage(
                Strings.format("`@: @`", playerName, message)
        ).queue();
    }

    public static void sendServerAction(String message) {
        sendServerAction(message, config.server);
    }
    public static void sendServerAction(String message, String server) {
        if (!isConnected) return;
        getServerLogChannel(server).sendMessage(message).queue();
    }

    public static void sendJoinLeaveEventMessage(String playerName, Boolean join) {
        sendJoinLeaveEventMessage(playerName, config.server, join);
    }

    public static void sendJoinLeaveEventMessage(String playerName, String server, Boolean join) {
        if (!isConnected) return;
        getServerLogChannel(server).sendMessage(
                Strings.format("`@` " + (join ? "joined" : "left"), playerName)
        ).queue();
    }
    public static void sendBanEvent(String targetName, String adminName) {
        sendBanEvent(targetName, adminName, config.server);
    }
    public static void sendBanEvent(String targetName, String adminName, String server) {
        if (!isConnected) return;

        EmbedBuilder embed = new EmbedBuilder().setTitle("Ban")
                .setColor(Color.red)
                .addField("Violator", targetName, false)
                .addField("Admin", adminName, false)
                .addField("Server", server, false);
        bansChannel.sendMessageEmbeds(embed.build()).addActionRow(Button.danger("editban", "Edit reason and date")).queue();
    }
}
