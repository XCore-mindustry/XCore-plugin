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
    /** The account that played; {@link #uuid} is the owner after account merges. */
    @BsonProperty("participant_uuid")
    public String participantUuid;

    /** One interval per connection; reconnects add intervals to the same row. */
    @BsonProperty("presence_intervals")
    @Builder.Default public List<TeamGameData> presenceIntervals = new ArrayList<>();
    @BsonProperty("present_ms")
    public long presentMs;

    /** Written by the statistics policy at storage time; a rebuild recomputes it from the raw data. */
    public String outcome;
    @BsonProperty("outcome_reason")
    public String outcomeReason;
    @BsonProperty("outcome_rules_version")
    public int outcomeRulesVersion;

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

    @BsonProperty("played_to_end")
    public boolean playedToEnd;

    @BsonProperty("placement")
    public Integer placement;

    public boolean isWinner;
}
