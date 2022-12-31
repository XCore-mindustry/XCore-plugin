package org.xcore.plugin.comp;

import org.jetbrains.annotations.NotNull;

public class PlayerData implements Comparable<PlayerData>{
    public String uuid;
    public String nickname;
    public Integer wins;

    public PlayerData(String uuid, String nickname, Integer wins) {
        this.uuid = uuid;
        this.nickname = nickname;
        this.wins = wins;
    }

    @Override
    public int compareTo(@NotNull PlayerData data) {
        if (wins == null || data.wins == null) {
            return 0;
        }
        return wins.compareTo(data.wins);
    }
}
