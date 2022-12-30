package org.xcore.plugin.comp;

import arc.struct.Seq;
import com.github.artbits.quickio.QuickIO;

public class Database {
    public static QuickIO.DB db;

    public static void load() {
        db = new QuickIO.DB("database");
    }
    public static PlayerData getPlayerData(String uuid) {
        var data = db.findFirst(PlayerData.class, p -> p.uuid.equals(uuid));

        if (data == null) {
            return new PlayerData(uuid);
        }

        return data;
    }

    public static void setPlayerData(PlayerData data) {
        if (data.id() == 0L) {
            db.save(data);
        } else {
            db.update(data, d -> data.uuid.equals(d.uuid));
        }
    }

    public static Seq<PlayerData> getLeaders() {
        return Seq.with(db.find(PlayerData.class, null, options ->
                options.sort("wins", 1).limit(10)));
    }
}
