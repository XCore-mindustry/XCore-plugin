package org.xcore.plugin.modules.discord;

import arc.util.Strings;
import arc.util.Time;
import fr.xpdustry.javelin.JavelinPlugin;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.listeners.SocketEvents;
import org.xcore.plugin.utils.Database;
import org.xcore.plugin.utils.models.BanData;

import java.util.concurrent.TimeUnit;

import static org.xcore.plugin.PluginVars.*;
import static org.xcore.plugin.modules.discord.Bot.adminRole;

public class DiscordListeners extends ListenerAdapter {
    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild() || event.getMessage().getContentRaw().isEmpty()) return;

        if (!globalConfig.servers.containsValue(event.getChannel().getIdLong(), false) && !event.getMessage().getContentRaw().startsWith("/"))
            return;

        String server = globalConfig.servers.findKey(event.getChannel().getIdLong(), false);

        if (server.equals(config.server)) {
            XcorePlugin.sendMessageFromDiscord(event.getAuthor().getName(), event.getMessage().getContentRaw());
        } else {
            JavelinPlugin.getJavelinSocket().sendEvent(
                    new SocketEvents.DiscordMessageEvent(event.getAuthor().getName(), event.getMessage().getContentRaw(), server)
            );
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().equals("editban")) {

            if (!event.getMember().getRoles().contains(adminRole)) return;

            TextInput reason = TextInput.create("reason", "Reason", TextInputStyle.SHORT)
                    .setPlaceholder("Reason of ban")
                    .setRequiredRange(3, 200)
                    .build();

            TextInput date = TextInput.create("date", "Unban date", TextInputStyle.SHORT)
                    .setPlaceholder("Ban duration (in days)")
                    .setRequiredRange(1, 20)
                    .build();

            Modal modal = Modal.create("editban", "Edit reason and ban duration")
                    .addActionRows(ActionRow.of(reason), ActionRow.of(date))
                    .build();

            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().equals("editban")) {
            String reason = event.getValue("reason").getAsString();
            String date = event.getValue("date").getAsString();

            BanData ban = activeBanData.get(event.getMessage().getIdLong());
            ban.reason = reason;
            ban.unbanDate = Time.millis() + TimeUnit.DAYS.toMillis(Strings.parseInt(date));

            event.getMessage().editMessageEmbeds(new EmbedBuilder(event.getMessage().getEmbeds().get(0))
                    .setAuthor(event.getUser().getName(), event.getUser().getEffectiveAvatarUrl(), event.getUser().getEffectiveAvatarUrl())
                    .addField("Reason", reason, false)
                    .addField("Unban date", TimeFormat.DATE_LONG.format(ban.unbanDate), false)
                    .build()).setActionRow(Button.primary("editban", "Edit reason and date").asDisabled()).queue();

            event.reply("Successful.").setEphemeral(true).queue();
            Database.setBan(ban);
            activeBanData.remove(event.getMessage().getIdLong());
        }
    }
}
