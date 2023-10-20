package org.xcore.plugin.modules.discord;

import discord4j.core.event.domain.interaction.ButtonInteractionEvent;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.Button;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.core.spec.MessageEditSpec;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;

import static org.xcore.plugin.modules.discord.Bot.gateway;

public class PaginationUtil {
    public static void paginate(MessageContext context, PaginationContent content) {
        final int[] currentPage = {1};

        var embed = EmbedCreateSpec.builder();
        int pages = content.get(embed, currentPage[0]);

        context.channel().createMessage(MessageCreateSpec.builder()
                .addEmbed(embed.build())
                .components(pages == 0
                        ? Collections.emptyList()
                        : Collections.singletonList(buttons(true, pages == 1)))
                .build()).subscribe(message -> gateway.on(ButtonInteractionEvent.class, e -> {
                    if (!DiscordHelper.buttonFilter(e, context, message)) return Mono.empty();

                    currentPage[0] = e.getCustomId().equals("prev") ? currentPage[0] - 1 : currentPage[0] + 1;
                    var edited = EmbedCreateSpec.builder();
                    content.get(edited, currentPage[0]);

                    return message.edit(MessageEditSpec.builder()
                            .embeds(Collections.singletonList(edited.build()))
                            .components(Collections.singletonList(buttons(currentPage[0] == 1, currentPage[0] == pages)))
                            .build()).and(e.deferEdit());
                })
                .timeout(Duration.ofMinutes(10))
                .onErrorResume(TimeoutException.class, exception -> Mono.empty())
                .subscribe());
    }

    private static ActionRow buttons(boolean leftDisabled, boolean rightDisabled) {
        return ActionRow.of(
                Button.success("prev", "<--").disabled(leftDisabled),
                Button.success("next", "-->").disabled(rightDisabled)
        );
    }

    public interface PaginationContent {
        int get(EmbedCreateSpec.Builder embed, int page);
    }
}