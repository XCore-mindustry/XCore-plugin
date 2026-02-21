package org.xcore.plugin.model;

import lombok.*;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerGameStats {
    public String nickname;
    public String uuid;

    @BsonProperty("join_time")
    public long joinTime;
    @BsonProperty("leave_time")
    public long leaveTime;

    @Builder.Default public List<TeamGameData> teams = new ArrayList<>();

    @BsonProperty("initial_team")
    public String initialTeam;
    @BsonProperty("final_team")
    public String finalTeam;

    @BsonProperty("blocks_built")
    public int blocksBuilt;
    @BsonProperty("blocks_deconstructed")
    public int blocksDeconstructed;
    @BsonProperty("blocks_destroyed")
    public int blocksDestroyed;

    @BsonProperty("units_produced")
    public int unitsProduced;
    @BsonProperty("units_destroyed")
    public int unitsDestroyed;

    public boolean isWinner;
}