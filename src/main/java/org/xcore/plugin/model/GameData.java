package org.xcore.plugin.model;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;
import org.xcore.plugin.model.enums.FinishReason;
import org.xcore.plugin.model.enums.VictoryType;

import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class GameData extends ModelData {

    public ObjectId map;
    @BsonProperty("game_mode")
    @Builder.Default public String gameMode = "Unknown";

    public ObjectId event;
    @BsonProperty("is_event")
    @Builder.Default public boolean isEvent = false;

    @BsonProperty("server_name")
    public String serverName;

    @BsonProperty("active_patches")
    @Builder.Default public List<String> activePatches = new ArrayList<>();
    @BsonProperty("active_mods")
    @Builder.Default public List<String> activeMods = new ArrayList<>();
    @BsonProperty("player_stats")
    @Builder.Default public List<PlayerGameStats> playerStats = new ArrayList<>();

    @BsonProperty("waves_reached")
    public int wavesReached;
    @BsonProperty("winning_team")
    public String winningTeam;

    @BsonProperty("finish_reason")
    @Builder.Default public FinishReason finishReason = FinishReason.NATURAL;
    @BsonProperty("victory_type")
    public VictoryType victoryType;

    @Builder.Default public int like = 0;
    @Builder.Default public int dislike = 0;

    @BsonProperty("started_at")
    @Builder.Default public long startGameTime = 0;
    @BsonProperty("ended_at")
    @Builder.Default public long endGameTime = 0;

    public GameData(ObjectId map, String gameMode) {
        super();
        this.map = map;
        this.gameMode = gameMode;
    }

    public GameData(ObjectId map, String gameMode, ObjectId event) {
        super();
        this.map = map;
        this.gameMode = gameMode;
        this.event = event;
        this.isEvent = true;

        this.playerStats = new ArrayList<>();
        this.activePatches = new ArrayList<>();
        this.activeMods = new ArrayList<>();
    }
}
