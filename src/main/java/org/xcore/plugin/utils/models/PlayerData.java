package org.xcore.plugin.utils.models;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import mindustry.gen.Player;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.xcore.plugin.modules.hexed.HexedRanks;

import static org.xcore.plugin.PluginVars.database;

@Accessors(chain = true)
public class PlayerData {
    public int pid;

    public String uuid = "";
    @Setter
    public String ip = "";

    @Setter
    public String nickname = "<unknown>";
    public String translatorLanguage = "off";

    public int pvpRating = 0;
    public int hexedRank = 0;
    public int hexedPoints = 0;
    public int totalPlayTime = 0;

    public boolean leaderboard = true;

    @BsonIgnore
    @Getter(lazy = true)
    private final AdminData adminData = database.adminDatas.get(uuid);

    @BsonIgnore
    @Setter
    public transient Player player = null;
    @BsonIgnore
    public String adminModVersion = null;
    @BsonIgnore
    public long historySize = 0L;
    @BsonIgnore
    public transient boolean exists = true;
    @BsonIgnore
    public long lastUnload = 0;

    public PlayerData(String uuid, Boolean exists) {
        this.uuid = uuid;
        this.exists = exists;
    }

    @SuppressWarnings("unused")
    public PlayerData() {
    }

    public HexedRanks.HexedRank hexedRank() {
        return HexedRanks.HexedRank.values()[hexedRank];
    }

    public void hexedRank(HexedRanks.HexedRank rank) {
        this.hexedRank = rank.ordinal();
    }

    public void generatePid() {
        this.pid = database.getNextSequence("player_id");
    }

    public void save() {
        database.playerDatas.save(this);
    }
}