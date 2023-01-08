package org.xcore.plugin.comp;

import org.jetbrains.annotations.NotNull;

public class PlayerData implements Comparable<PlayerData>{
    public String uuid;
    public String nickname;
    public Integer rating;
    public Boolean exists;

    public PlayerData(String uuid, String nickname, Integer rating, Boolean exists) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.rating = rating;
        this.exists = exists;
    }

    @Override
    public int compareTo(@NotNull PlayerData data) {
        if (rating == null || data.rating == null) {
            return 0;
        }
        return rating.compareTo(data.rating);
    }
}
