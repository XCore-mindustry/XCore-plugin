package org.xcore.plugin.modules;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.support.ConnectionSource;
import com.j256.ormlite.table.TableUtils;
import mindustry.gen.Player;
import org.xcore.plugin.XcorePlugin;
import org.xcore.plugin.modules.models.PlayerData;

import java.sql.SQLException;
import java.util.List;

public class Database {
    public static String connectionURL = "jdbc:sqlite:database.db";
    public static ConnectionSource conn;
    public static Dao<PlayerData, String> playerDataDao;

    public static ObjectMap<String, PlayerData> cachedPlayerData = new ObjectMap<>();

    public static void init() {
        try (ConnectionSource connectionSource = new JdbcConnectionSource(connectionURL)) {
            conn = connectionSource;
            playerDataDao = DaoManager.createDao(connectionSource, PlayerData.class);

            TableUtils.createTableIfNotExists(connectionSource, PlayerData.class);
        } catch (Exception e) {
            XcorePlugin.err(e.getMessage());
        }
    }

    public static PlayerData getPlayerData(Player player) {
        return getPlayerData(player.uuid());
    }

    public static PlayerData getPlayerData(String uuid) {
        try {
            var data = playerDataDao.queryForId(uuid);

            if (data == null) {
                data = new PlayerData(uuid, "<unknown>", 0, false);
            }

            return data;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setPlayerData(PlayerData data) {
        try {
            playerDataDao.createOrUpdate(data);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Seq<PlayerData> getLeaders() {
        try {
            List<PlayerData> datas = playerDataDao.queryBuilder().orderBy("rating", false).limit(10L).query();
            return Seq.with(datas);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
