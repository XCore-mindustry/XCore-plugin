package org.xcore.plugin.model;

import lombok.*;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamGameData {
    public String team;

    @BsonProperty("join_time")
    public long joinTime;
    @BsonProperty("leave_time")
    public long leaveTime;
}
