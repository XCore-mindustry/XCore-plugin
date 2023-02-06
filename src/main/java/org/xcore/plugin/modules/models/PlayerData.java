package org.xcore.plugin.modules.models;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable(tableName = "players")
public class PlayerData {
    @DatabaseField(id = true, canBeNull = false)
    public String uuid;

    @DatabaseField(canBeNull = false, defaultValue = "<unknown>")
    public String nickname;

    @DatabaseField(defaultValue = "0")
    public int rating;

    public boolean exists = true;

    public PlayerData(String uuid, String nickname, Integer rating, Boolean exists) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.rating = rating;
        this.exists = exists;
    }

    @SuppressWarnings("unused")
    PlayerData() {
        // all persisted classes must define a no-arg constructor with at least package visibility
    }

    public PlayerData setNickname(String nickname) {
        this.nickname = nickname;
        return this;
    }
}