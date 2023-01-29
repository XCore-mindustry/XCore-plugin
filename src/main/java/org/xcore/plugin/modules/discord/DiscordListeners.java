package org.xcore.plugin.modules.discord;

import fr.xpdustry.javelin.JavelinPlugin;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.XcorePlugin;

import static org.xcore.plugin.PluginVars.config;
import static org.xcore.plugin.modules.ServersConfig.servers;
public class DiscordListeners extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild() || event.getMessage().getContentRaw().isEmpty()) return;

        if (!servers.containsValue(event.getChannel().getIdLong(), false) && !event.getMessage().getContentRaw().startsWith("/")) return;

        String server = servers.findKey(event.getChannel().getIdLong(), false);

        if (server.equals(config.server)) {
            XcorePlugin.sendMessageFromDiscord(event.getAuthor().getName(), event.getMessage().getContentRaw());
        } else {
            JavelinPlugin.getJavelinSocket().sendEvent(
                    new SocketEvents.DiscordMessageEvent(event.getAuthor().getName(), event.getMessage().getContentRaw(), server)
            );
        }

    }
}
