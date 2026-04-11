package org.xcore.plugin.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditActor {
    @Builder.Default
    public AuditActorType type = AuditActorType.SYSTEM;

    @Builder.Default
    public String id = "";

    @BsonProperty("name_snapshot")
    @Builder.Default
    public String nameSnapshot = "Unknown";

    @BsonProperty("display_name_snapshot")
    public String displayNameSnapshot;

    @BsonProperty("discord_id")
    public String discordId;

    @BsonProperty("player_uuid")
    public String playerUuid;

    public Integer pid;

    @BsonProperty("server_id")
    public String serverId;
}
