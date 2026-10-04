package org.xcore.protocol.generated.shared;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.xcore.protocol.generated.runtime.ProtocolPayload;

public record SeasonPodiumEntryV1(
        int place,
        PlayerRefV1 player,
        DiscordIdentityRefV1 discord,
        int rating,
        String league,
        int matches,
        int wins,
        List<SeasonPrizeV1> prizes
) implements ProtocolPayload {
    public SeasonPodiumEntryV1 {
        if (place < 1) {
            throw new IllegalArgumentException("place must be >= 1");
        }
        Objects.requireNonNull(player, "player must not be null");
        if (discord != null) {
            Objects.requireNonNull(discord, "discord must not be null");
        }
        Objects.requireNonNull(league, "league must not be null");
        if (league.length() < 1) {
            throw new IllegalArgumentException("league must be at least 1 characters");
        }
        if (matches < 0) {
            throw new IllegalArgumentException("matches must be >= 0");
        }
        if (wins < 0) {
            throw new IllegalArgumentException("wins must be >= 0");
        }
        if (prizes != null) {
            prizes = Objects.requireNonNull(prizes, "prizes must not be null");
            prizes = List.copyOf(prizes);
            for (SeasonPrizeV1 item : prizes) {
                Objects.requireNonNull(item, "prizes[] must not be null");
            }
        }
    }

    @Override
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("place", place);
        payload.put("player", player.toPayload());
        if (discord != null) {
            payload.put("discord", discord.toPayload());
        }
        payload.put("rating", rating);
        payload.put("league", league);
        payload.put("matches", matches);
        payload.put("wins", wins);
        if (prizes != null) {
            payload.put(
        "prizes",
        prizes.stream()
            .map(item -> item.toPayload())
            .toList()
    );
        }
        return payload;
    }
}
