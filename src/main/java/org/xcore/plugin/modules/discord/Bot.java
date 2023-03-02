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
import org.xcore.plugin.utils.models.BanData;

import java.awt.*;

import static net.dv8tion.jda.api.requests.GatewayIntent.*;
import static net.dv8tion.jda.api.utils.MemberCachePolicy.OWNER;
import static net.dv8tion.jda.api.utils.MemberCachePolicy.VOICE;
import static org.xcore.plugin.PluginVars.*;

public class Bot {
    public static JDA jda;
    public static TextChannel bansChannel;
    public static Role adminRole;

    public static boolean isConnected = false;

    /**
     * Базовый метод подключения сервера к Discord боту
     */
    public static void connect() {
        try {
            jda = JDABuilder.createLight(globalConfig.discordBotToken)
                    .disableCache(CacheFlag.ACTIVITY)
                    .setMemberCachePolicy(VOICE.or(OWNER))
                    .setChunkingFilter(ChunkingFilter.NONE)
                    .disableIntents(GUILD_MESSAGE_TYPING, GUILD_PRESENCES)
                    .setLargeThreshold(50)
                    .enableIntents(GUILD_MEMBERS, MESSAGE_CONTENT)
                    .addEventListeners(new DiscordListeners())
                    .build()
                    .awaitReady();

            bansChannel = jda.getTextChannelById(globalConfig.discordBansChannelId);
            adminRole = jda.getRoleById(globalConfig.discordAdminRoleId);
            isConnected = true;
        } catch (Exception e) {
            XcorePlugin.err("Error while connecting to discord: ");
            e.printStackTrace();
        }
    }

    /**
     * Получение канала Discord для взаимодействия
     *
     * @param server Название сервера
     * @return Текстовый Discord канал для отправки сообщений
     */
    public static TextChannel getServerLogChannel(String server) {
        return jda.getTextChannelById(globalConfig.servers.get(server));
    }

    /**
     * Отправка сообщения из чата в Discord. Простоновка сервера автоматическая.
     * Перегрузка метода: {@link Bot#sendMessageEventMessage(String, String, String)}
     *
     * @param playerName Ник игрока
     * @param message    Текст сообщения
     */
    public static void sendMessageEventMessage(String playerName, String message) {
        sendMessageEventMessage(playerName, message, config.server);
    }

    /**
     * Отправка сообщения из чата в Discord. Простоновка сервера автоматическая.
     * Упрощенная версия с автоматической простановкой сервера:
     * {@link Bot#sendMessageEventMessage(String, String)}
     *
     * @param playerName Ник игрока
     * @param message    Текст сообщения
     */
    public static void sendMessageEventMessage(String playerName, String message, String server) {
        if (!isConnected) return;
        getServerLogChannel(server).sendMessage(
                Strings.format("`@: @`", playerName, message)
        ).queue();
    }

    /**
     * Отправка события сервера. Название сервера проставляется автоматически
     * Перегрузка данного метода: {@link Bot#sendServerAction(String, String)}
     *
     * @param message Сообщение о событии
     */
    public static void sendServerAction(String message) {
        sendServerAction(message, config.server);
    }

    /**
     * Отправка события сервера. Название сервера проставляется автоматически
     * Перегрузка данного метода: {@link Bot#sendServerAction(String)}
     *
     * @param message Сообщение о событии
     * @param server  Название сервера
     */
    public static void sendServerAction(String message, String server) {
        if (!isConnected) return;
        getServerLogChannel(server).sendMessage(message).queue();
    }

    /**
     * Отправка сообщения о входе/выходе игрока. Название сервера проставляется автоматически
     * Упрощенный вариант с автоматической простановкой сервера:
     * {@link Bot#sendServerAction(String)}
     *
     * @param playerName Ник игрока
     * @param join       true - при входе, false - при выходе
     */
    public static void sendJoinLeaveEventMessage(String playerName, Boolean join) {
        sendJoinLeaveEventMessage(playerName, config.server, join);
    }

    /**
     * Отправка сообщения о входе/выходе игрока
     * Упрощенный вариант с автоматической простановкой сервера:
     * {@link Bot#sendJoinLeaveEventMessage(String, Boolean)}
     *
     * @param playerName Ник игрока
     * @param server     Название сервера
     * @param join       true - при входе, false - при выходе
     */
    public static void sendJoinLeaveEventMessage(String playerName, String server, Boolean join) {
        if (!isConnected) return;
        getServerLogChannel(server).sendMessage(
                Strings.format("`@` " + (join ? "joined" : "left"), playerName)
        ).queue();
    }

    /**
     * Отправка события об бане в Discord
     *
     * @param ban Исформация о бане. Смотри {@link BanData}
     */
    public static void sendBanEvent(BanData ban) {
        if (!isConnected) return;

        EmbedBuilder embed = new EmbedBuilder().setTitle("Ban")
                .setColor(Color.red)
                .addField("Violator", ban.name, false)
                .addField("Admin", ban.adminName, false)
                .addField("Server", ban.server, false);

        bansChannel.sendMessageEmbeds(embed.build()).addActionRow(Button.danger("editban", "Edit reason and ban duration"))
                .queue(m -> activeBanData.put(m.getIdLong(), ban));
    }
}
