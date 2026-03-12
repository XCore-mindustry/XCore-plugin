package org.xcore.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class DiscordLinkCode extends ModelData {

    @Builder.Default
    public String code = "";

    @BsonProperty("player_uuid")
    @Builder.Default public String playerUuid = "";

    @BsonProperty("player_pid")
    @Builder.Default public int playerPid = -1;

    @BsonProperty("player_nickname")
    @Builder.Default public String playerNickname = "Unknown";

    @Builder.Default public String server = "";

    @BsonProperty("expires_at")
    @Builder.Default public long expiresAt = 0L;

    @BsonProperty("consumed_at")
    @Builder.Default public long consumedAt = 0L;

    @BsonProperty("consumed_by_discord_id")
    @Builder.Default public String consumedByDiscordId = "";

    @Builder.Default public String status = "pending";

    public boolean isExpired(long now) {
        return expiresAt > 0L && expiresAt <= now;
    }

    public boolean isPending() {
        return "pending".equals(status);
    }
}
