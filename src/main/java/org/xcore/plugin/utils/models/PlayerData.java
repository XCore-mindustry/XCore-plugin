package org.xcore.plugin.utils.models;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.xcore.plugin.modules.hexed.HexedRanks;

public class PlayerData {
    public String uuid;
    public String nickname = "<unknown>";
    public String translatorLanguage = "off";

    public int pvpRating = 0;

    public int hexedRank = 0;
    public int hexedPoints = 0;
    public int playTime = 0;
    public int totalPlayTime = 0;

    public boolean leaderboard = true;
    public boolean jsAccess = false;

    public long muted = 0;

    @BsonIgnore
    public String adminModVersion = null;
    @BsonIgnore
    public long historySize = 0L;
    @BsonIgnore
    public boolean exists = true;

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

    public PlayerData setNickname(String nickname) {
        this.nickname = nickname;
        return this;
    }
}