package org.xcore.plugin.model;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerGameStats {
    public String nickname;

    public long joinTime;
    public long leaveTime; // 0 якщо був до кінця

    @Builder.Default public List<TeamGameData> teams = new ArrayList<>();

    public String initialTeam; // Команда при заході
    public String finalTeam;   // Команда в кінці гри

    // Статистика дій
    public int blocksBuilt;
    public int blocksDeconstructed; // Розбирання молотком
    public int blocksDestroyed;     // Руйнування ворожих блоків у бою

    public int unitsProduced;
    public int unitsDestroyed;

    public boolean isWinner;
}