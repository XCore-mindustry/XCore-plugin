package org.xcore.plugin.comp;

import org.jetbrains.annotations.NotNull;

public class PlayerData implements Comparable<PlayerData>{
    public String uuid;
    public String nickname;
    public Integer rating;

    public PlayerData(String uuid, String nickname, Integer rating) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.rating = rating;
    }

    @Override
    public int compareTo(@NotNull PlayerData data) {
        if (rating == null || data.rating == null) {
            return 0;
        }
        return rating.compareTo(data.rating);
    }
}
