package org.xcore.plugin.modules.models;

import org.bson.codecs.pojo.annotations.BsonIgnore;

public class PlayerData {
    public String uuid;

    public String nickname = "<unknown>";

    public int pvpRating = 0;
    public int hexedWins = 0;

    public String translatorLanguage = "off";

    @BsonIgnore
    public boolean exists = true;

    public PlayerData(String uuid, Boolean exists) {
        this.uuid = uuid;
        this.exists = exists;
    }

    @SuppressWarnings("unused")
    public PlayerData() {
    }

    public PlayerData setNickname(String nickname) {
        this.nickname = nickname;
        return this;
    }
}