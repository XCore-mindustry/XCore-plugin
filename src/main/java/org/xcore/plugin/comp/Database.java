package org.xcore.plugin.comp;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Strings;
import mindustry.gen.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Database {
    public static String connectionURL = "jdbc:sqlite:database.db";
    public static ObjectMap<String, PlayerData> cachedPlayerData= new ObjectMap<>();
    public static void load() {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection conn = DriverManager.getConnection(connectionURL);
            conn.createStatement().execute(
                    "CREATE TABLE IF NOT EXISTS players (uuid TEXT, nickname TEXT, rating INTEGER, UNIQUE(uuid));"
            );
            conn.close();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }


    }
    public static PlayerData getPlayerData(Player player) {
        try (Connection conn = DriverManager.getConnection(connectionURL)) {
            var result = conn.createStatement().executeQuery(
                    "SELECT * FROM players WHERE uuid = '" + player.uuid() + "'"
            );
            PlayerData data = new PlayerData(player.uuid(), player.coloredName(), 0);
            while (result.next()) {
                data = new PlayerData(
                        player.uuid(),
                        player.name,
                        result.getInt("rating"));
            }
            return data;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setPlayerData(PlayerData data) {
        try (Connection conn = DriverManager.getConnection(connectionURL)) {
            conn.createStatement().execute(Strings.format(
                    "INSERT OR REPLACE INTO players(uuid, nickname, rating) VALUES('@', '@', @)"
                    , data.uuid, data.nickname, data.rating));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public static Seq<PlayerData> getLeaders() {
        try (Connection conn = DriverManager.getConnection(connectionURL)) {
            var result = conn.createStatement().executeQuery("SELECT * FROM players ORDER BY rating DESC LIMIT 10");

            Seq<PlayerData> datas = new Seq<>();
            while (result.next()) {
                datas.add(new PlayerData(result.getString("uuid"),
                        result.getString("nickname"),
                        result.getInt("rating")));
            }
            return datas;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
