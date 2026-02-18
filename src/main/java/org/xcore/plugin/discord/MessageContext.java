package org.xcore.plugin.discord;

import arc.func.Cons;
import arc.util.Strings;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.MessageReference;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.object.entity.*;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.*;
import discord4j.core.spec.EmbedCreateSpec.Builder;
import discord4j.discordjson.json.MessageReferenceData;
import discord4j.rest.util.Color;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.PlayerData;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;

import static arc.util.Strings.format;

public record MessageContext(Message message, Member member, MessageChannel channel, GlobalConfig globalConfig) {
    public MessageCreateMono success(String title, String content, Object... values) {
        return success(embed -> embed.title(title).description(format(content, values)));
    }

    public MessageCreateMono success(Cons<Builder> cons) {
        return reply(embed -> {
            embed.color(Color.MEDIUM_SEA_GREEN);
            cons.get(embed);
        });
    }

    public MessageCreateMono error(String title, String content, Object... values) {
        return error(embed -> embed.title(title).description(format(content, values)));
    }

    public MessageCreateMono error(Cons<Builder> cons) {
        return reply(embed -> {
            embed.color(Color.CINNABAR);
            cons.get(embed);
        });
    }

    public MessageCreateMono info(String title, String content, Object... values) {
        return info(embed -> embed.title(title).description(format(content, values)));
    }

    public MessageCreateMono info(Cons<Builder> cons) {
        return reply(embed -> {
            embed.color(Color.SUMMER_SKY);
            cons.get(embed);
        });
    }

    public MessageCreateMono reply(Cons<Builder> cons) {
        var embed = EmbedCreateSpec.builder();
        cons.get(embed);

        return reply(embed.build());
    }

    public MessageCreateMono reply(EmbedCreateSpec embed) {
        return channel.createMessage(embed).withMessageReference(
                MessageReferenceData.builder()
                        .type(MessageReference.Type.DEFAULT.getValue())
                        .messageId(message.getId().asLong())
                        .build()
        );
    }


    public boolean hasRole(long roleId) {
        return member.getRoleIds().contains(Snowflake.of(roleId));
    }
    public boolean checkId(int id) {
        if (id < 0) {
            error("Invalid number", "'player-id' must be a positive number!").subscribe();
            return true;
        }
        return false;
    }
    public boolean checkPeriod(Duration period) {
        if (period == null) {
            error(
                    "Wrong period format",
                    "The period must be a number or in the format \"number<m/h/d/y> (minutes/hours/days/years)"
            ).subscribe();
            return true;
        }
        return false;
    }
    public boolean playerNotFound(PlayerData data) {
        if (data == null) {
            error("Player not found", "Check if the input is correct.").subscribe();
            return true;
        }
        return false;
    }
    public boolean serverNotFound(String serverName) {
        if (serverName == null || !globalConfig.servers.containsKey(serverName)) {
            error(
                    "Invalid server name",
                    "Server with provided name not found!\nServers: " + Strings.join(", ", globalConfig.servers.keys())
            ).subscribe();
            return true;
        }
        return false;
    }
    public void noResponse() {
        error(
                "Internal Error",
                "The server did not respond. Perhaps the server is down or an error has occurred."
        ).subscribe();
    }

    public boolean buttonFilter(ButtonInteractionEvent event, Message message) {
        var member = event.getInteraction().getMember().orElseThrow();
        return member().getId().equals(member.getId()) && message.getId().equals(event.getMessageId());
    }

    public interface PaginationContent {
        int get(EmbedCreateSpec.Builder embed, int page);
    }

    public void paginate(PaginationContent content) {
        final int[] currentPage = {1};

        var embed = EmbedCreateSpec.builder();
        int pages = content.get(embed, currentPage[0]);

        channel().createMessage(MessageCreateSpec.builder()
                .addEmbed(embed.build())
                .components(pages == 0
                        ? Collections.emptyList()
                        : Collections.singletonList(createPaginationButtons(true, pages == 1)))
                .build()).subscribe(message -> channel.getClient().on(ButtonInteractionEvent.class, e -> {
                    if (!buttonFilter(e, message)) return Mono.empty();

                    currentPage[0] = e.getCustomId().equals("prev") ? currentPage[0] - 1 : currentPage[0] + 1;
                    var edited = EmbedCreateSpec.builder();
                    content.get(edited, currentPage[0]);

                    return message.edit(MessageEditSpec.builder()
                            .addEmbed(edited.build())
                            .addComponent(createPaginationButtons(currentPage[0] == 1, currentPage[0] == pages))
                            .build()).and(e.deferEdit());
                })
                .timeout(Duration.ofMinutes(10))
                .onErrorResume(TimeoutException.class, _ -> Mono.empty())
                .subscribe());
    }

    private ActionRow createPaginationButtons(boolean leftDisabled, boolean rightDisabled) {
        return ActionRow.of(
                Button.secondary("prev", "←").disabled(leftDisabled),
                Button.secondary("next", "→").disabled(rightDisabled)
        );
    }
}
