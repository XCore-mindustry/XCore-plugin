package org.xcore.plugin.modules.discord;

import arc.util.Strings;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.xcore.plugin.XcorePlugin;

import static net.dv8tion.jda.api.requests.GatewayIntent.GUILD_MEMBERS;
import static net.dv8tion.jda.api.requests.GatewayIntent.MESSAGE_CONTENT;
import static org.xcore.plugin.modules.ServersConfig.servers;
import static org.xcore.plugin.PluginVars.*;

public class Bot {
    public static JDA jda;
    public static Role adminRole;

    public static boolean isConnected = false;
    public static void connect() {
        try {
            jda = JDABuilder.createLight(config.discordBotToken)
                    .enableIntents(GUILD_MEMBERS, MESSAGE_CONTENT)
                    .addEventListeners(new DiscordListeners())
                    .build()
                    .awaitReady();

            isConnected = true;
            adminRole = jda.getRoleById(config.discordAdminRoleId);
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
        getServerLogChannel(server).sendMessage(
                Strings.format("`@: @`", playerName, message)
        ).queue();
    }

    public static void sendServerAction(String message) {
        sendServerAction(message, config.server);
    }
    public static void sendServerAction(String message, String server) {
        getServerLogChannel(server).sendMessage(message).queue();
    }

    public static void sendJoinLeaveEventMessage(String playerName, Boolean join) {
        sendJoinLeaveEventMessage(playerName, config.server, join);
    }

    public static void sendJoinLeaveEventMessage(String playerName, String server, Boolean join) {
        getServerLogChannel(server).sendMessage(
                Strings.format("`@` " + (join ? "joined" : "left"), playerName)
        ).queue();
    }
}
