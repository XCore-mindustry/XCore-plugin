package org.xcore.plugin.discord;

import arc.util.Log;
import arc.util.Strings;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.emoji.UnicodeEmoji;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.http.client.ClientException;
import discord4j.rest.util.AllowedMentions;
import discord4j.rest.util.Color;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.database.repository.PlayerDataRepository;
import org.xcore.plugin.model.BanData;
import org.xcore.plugin.model.PlayerData;

import java.util.Optional;

@Singleton
public class DiscordLogBridge {

    private final GlobalConfig globalConfig;
    private final PlayerDataRepository playerDataRepository;

    private MessageChannel bansChannel;
    private MessageChannel privateChannel;
    private GatewayDiscordClient gateway;
    private boolean isConnected = false;

    @Inject
    public DiscordLogBridge(
            GlobalConfig globalConfig,
            PlayerDataRepository playerDataRepository
    ) {
        this.globalConfig = globalConfig;
        this.playerDataRepository = playerDataRepository;
    }

    public void initialize(GatewayDiscordClient gateway, boolean isConnected) {
        this.gateway = gateway;
        this.isConnected = isConnected;

        if (isConnected) {
            this.bansChannel = safeGetChannel(globalConfig.discordBansChannelId, "Bans");
            this.privateChannel = safeGetChannel(globalConfig.discordPrivateChannelId, "Private/Admin");
        }
    }

    public void sendMessageEvent(String playerName, String message, String server) {
        if (!isConnected) return;

        if (globalConfig.servers.get(server) == null) {
            Log.err("@ server has no log channel id!", server);
            return;
        }

        getServerLogChannel(server).ifPresent(c -> sendMessage(c, Strings.format("`@: @`", playerName, message)));
    }

    /**
     * Send a connection event (join/leave) to the server's Discord log channel
     */
    public void sendConnectionEvent(String playerName, String server, Boolean join) {
        if (!isConnected) return;
        getServerLogChannel(server).ifPresent(c -> sendMessage(c, Strings.format("`@` " + (join ? "joined" : "left"), playerName)));
    }

    /**
     * Send a ban notification to the bans channel
     */
    public void sendBan(BanData ban) {
        if (!isConnected || bansChannel == null) return;

        PlayerData data = playerDataRepository.findByUuid(ban.uuid);

        bansChannel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder()
                        .title("Ban")
                        .color(Color.RED)
                        .addField("ID", String.valueOf(data == null ? -1 : data.pid), false)
                        .addField("Violator", ban.name, false)
                        .addField("Admin", ban.adminName, false)
                        .addField("Reason", ban.reason, false)
                        .addField("Unban Date", discord4j.common.util.TimestampFormat.LONG_DATE.format(ban.expireDate), false)
                        .build()
                )
                .build()).subscribe();
    }

    /**
     * Send an admin request notification to the private channel with confirmation buttons
     */
    public void sendAdminRequestEvent(int pid, String server) {
        if (!isConnected || privateChannel == null) return;

        PlayerData data = playerDataRepository.findByPid(pid);

        privateChannel.createMessage(MessageCreateSpec.builder()
                .addEmbed(EmbedCreateSpec.builder().title("Admin Request")
                        .color(Color.RED)
                        .addField("Name", data.nickname, false)
                        .addField("Server", server, false)
                        .build())
                .addComponent(ActionRow.of(
                        Button.success(server + "_" + pid + "_admreq", UnicodeEmoji.unicode("✅"), "Confirm"),
                        Button.danger("decline", UnicodeEmoji.unicode("❌"), "Decline")))
                .build()).subscribe();
    }

    /**
     * Get the log channel for a specific server
     */
    public Optional<MessageChannel> getServerLogChannel(String server) {
        var id = globalConfig.servers.get(server);
        if (id == null) {
            Log.err("@ server has no log channel id!", server);
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(gateway.getChannelById(Snowflake.of(id)).ofType(MessageChannel.class).block());
        } catch (Exception e) {
            Log.warn("Failed to get log channel for server '@' (ID: @): @", server, id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Send a message to a Discord channel
     */
    public void sendMessage(MessageChannel channel, String message) {
        if (!isConnected || channel == null) return;
        channel.createMessage(MessageCreateSpec.builder()
                .content(message)
                .allowedMentions(AllowedMentions.suppressAll())
                .build()).subscribe();
    }

    /**
     * Safely get a channel by ID, with error handling and logging
     */
    private MessageChannel safeGetChannel(long id, String name) {
        if (id == 0L) {
            Log.warn("Discord channel '@' ID is not configured (0). Feature disabled.", name);
            return null;
        }

        try {
            return gateway.getChannelById(Snowflake.of(id))
                    .ofType(MessageChannel.class)
                    .block();
        } catch (ClientException e) {
            if (e.getStatus().code() == 404) {
                Log.warn("Discord channel '@' (ID: @) not found (404). Check your config.", name, id);
            } else if (e.getStatus().code() == 403) {
                Log.warn("Discord bot has no access to channel '@' (ID: @).", name, id);
            } else {
                Log.warn("Could not fetch Discord channel '@' (ID: @): @", name, id, e.getMessage());
            }
            return null;
        } catch (Exception e) {
            Log.warn("Unexpected error fetching Discord channel '@': @", name, e.getMessage());
            return null;
        }
    }
}
