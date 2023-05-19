package tilelogger;

import java.util.HashMap;

import arc.util.Nullable;
import mindustry.gen.Player;

public class PlayerConfig {
    public int history_size;
    public int select;
    public Rect rect = new Rect();    

    public static PlayerConfig get(@Nullable Player player) {
        return player_configs_.computeIfAbsent(player == null ? "" : player.uuid(), s -> new PlayerConfig());
    }

    private static HashMap<String, PlayerConfig> player_configs_ = new HashMap<String, PlayerConfig>();
}
