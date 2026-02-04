package org.xcore.plugin.model;

import lombok.Setter;
import lombok.experimental.Accessors;
import mindustry.gen.Player;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;
import org.xcore.plugin.gamemode.hexed.HexedRanks;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Accessors(chain = true)
public class PlayerData {
    @BsonId
    public ObjectId id;

    public int pid;
    public String uuid = "";

    @Setter public String ip = "";
    @Setter public String nickname = "<unknown>";
    public String translatorLanguage = "off";

    public int pvpRating = 0;
    public int hexedRank = 0;
    public int hexedPoints = 0;
    public int totalPlayTime = 0;

    public Map<String, Boolean> eventVotes = new HashMap<>();
    public Map<String, Boolean> mapVotes = new HashMap<>();

    public boolean leaderboard = true;

    @BsonIgnore @Setter public transient Player player = null;
    @BsonIgnore public String adminModVersion = null;
    @BsonIgnore public long historySize = 0L;
    @BsonIgnore public transient boolean exists = true;
    @BsonIgnore public long lastUnload = 0;

    public PlayerData(String uuid, Boolean exists) {
        this.uuid = uuid;
        this.exists = exists;
    }

    public PlayerData() {}

    public HexedRanks.HexedRank hexedRank() {
        return HexedRanks.HexedRank.values()[hexedRank];
    }

    public void hexedRank(HexedRanks.HexedRank rank) {
        this.hexedRank = rank.ordinal();
    }
}
