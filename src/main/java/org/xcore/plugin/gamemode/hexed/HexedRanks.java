package org.xcore.plugin.gamemode.hexed;

import mindustry.gen.Player;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.model.PlayerData;

public class HexedRanks {
    public static void updateRank(Player player, PlayerData data, TomlXcoreConfig config) {
        if (!"mini-hexed".equals(config.server.name) || player == null || data == null) return;
    }

    public enum HexedRank {
        newbie() {},

        regular(newbie) {{
            tag = "[cyan]<[accent]\uF7E7[cyan]>[]";
            requirements = new Requirements(3);
        }},

        advanced(regular) {{
            tag = "[cyan]<[accent]\uF7ED[cyan]>[]";
            requirements = new Requirements(10);
        }},

        veteran(advanced) {{
            tag = "[cyan]<[accent]\uF7EC[cyan]>[]";
            requirements = new Requirements(20);
        }},

        davastator(veteran) {{
            tag = "[cyan]<[accent]\uF7C4[cyan]>[]";
            requirements = new Requirements(25);
        }},

        the_legend(davastator) {{
            tag = "[cyan]<[accent]\uF7C6[cyan]>[]";
            requirements = new Requirements(30);
        }};

        public String tag = "";
        public HexedRank next;
        public Requirements requirements;

        HexedRank() {
        }

        HexedRank(HexedRank previous) {
            previous.next = this;
        }

        public boolean hasNext() {
            return next != null && next.requirements != null;
        }

        public boolean checkNext(int wins) {
            return hasNext() && next.requirements.check(wins);
        }
    }

    public record Requirements(int wins) {
        public boolean check(int wins) {
            return wins >= this.wins;
        }
    }

}
