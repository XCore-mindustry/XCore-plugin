package org.xcore.plugin.model;

import lombok.*;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
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

    ObjectId map;
    @Builder.Default public String gameMode = "Unknown";

    ObjectId event;
    @Builder.Default public boolean isEvent = false;

    public String serverName;

    @Builder.Default public List<String> activePatches = new ArrayList<>();
    @Builder.Default public List<String> activeMods = new ArrayList<>();
    @Builder.Default public List<PlayerGameStats> playerStats = new ArrayList<>();

    public int wavesReached;
    public String winningTeam;

    @Builder.Default public FinishReason finishReason = FinishReason.NATURAL;
    public VictoryType victoryType;

    @Builder.Default public int like = 0;
    @Builder.Default public double dislike = 0;

    @Builder.Default public long startGameTime = 0;
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
    }
}
