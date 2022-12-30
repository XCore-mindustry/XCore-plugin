package org.xcore.plugin.comp;

import com.github.artbits.quickio.QuickIO;

public class PlayerData extends QuickIO.Object {
    public String uuid;
    public Integer wins = 0;

    public PlayerData(String uuid) {
        this.uuid = uuid;
    }

    public PlayerData(String uuid, Integer wins) {
        this.uuid = uuid;
        this.wins = wins;
    }
}
