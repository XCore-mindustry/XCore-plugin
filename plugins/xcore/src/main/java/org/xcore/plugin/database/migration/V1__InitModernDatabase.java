package org.xcore.plugin.database.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;
import jakarta.inject.Singleton;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class V1__InitModernDatabase implements Migration {
    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public String getDescription() {
        return "Migrate admins to players, rename fields to snake_case, and initialize schema version";
    }

    @Override
    public void up(MongoDatabase database) {
        renameBaseFields(database);
        renameEventFields(database);
        renamePlayerFields(database);
        renameMapFields(database);
        renamePunishmentFields(database);

        migrateAdmins(database);
    }

    private void renameBaseFields(MongoDatabase database) {
        String[] collections = {"players", "maps", "events", "games", "bans", "mutes"};
        for (String col : collections) {
            database.getCollection(col).updateMany(new Document(), new Document("$rename", new Document()
                    .append("createdModelTime", "created_at")
                    .append("editModelTime", "updated_at")
                    .append("deleteModelTime", "deleted_at")
                    .append("versionModel", "version")
                    .append("isVisible", "is_visible")
            ));

            database.getCollection(col).updateMany(
                new Document("version", new Document("$exists", false)),
                new Document("$set", new Document("version", 1))
            );
        }
    }

    private void renameEventFields(MongoDatabase database) {
        database.getCollection("events").updateMany(new Document(), new Document("$rename", new Document()
                .append("isMajor", "is_major")
                .append("isConducted", "is_finished")
                .append("isActive", "is_active")
                .append("isTemporary", "is_temporary")
                .append("plannedStartTime", "planned_start_at")
                .append("plannedEndTime", "planned_end_at")
                .append("startTime", "started_at")
                .append("endTime", "ended_at")
        ));
    }

    private void renamePlayerFields(MongoDatabase database) {
        database.getCollection("players").updateMany(new Document(), new Document("$rename", new Document()
                .append("ip", "last_ip")
                .append("password", "password_hash")
                .append("translatorLanguage", "translator_language")
                .append("pvpRating", "pvp_rating")
                .append("hexedRank", "hexed_rank")
                .append("hexedPoints", "hexed_points")
                .append("totalPlayTime", "total_play_time")
                .append("eventVotes", "event_votes")
                .append("mapVotes", "map_votes")
                .append("adminConfirmed", "admin_confirmed")
                .append("admin", "is_admin")
        ));
    }

    private void renameMapFields(MongoDatabase database) {
        database.getCollection("maps").updateMany(new Document(), new Document("$rename", new Document()
                .append("fileName", "file_name")
                .append("gameMode", "game_mode")
                .append("playedTimesYear", "played_times_year")
                .append("playedTimes", "play_count")
                .append("lastPlayedTime", "last_played_at")
                .append("minimumGameTime", "minimum_duration")
                .append("averageGameTime", "average_duration")
                .append("maximumGameTime", "maximum_duration")
        ));
    }

    private void renamePunishmentFields(MongoDatabase database) {
        String[] punishmentCols = {"bans", "mutes"};
        for (String col : punishmentCols) {
            database.getCollection(col).updateMany(new Document(), new Document("$rename", new Document()
                    .append("adminName", "admin_name")
                    .append("expireDate", "expire_date")
            ));
        }
    }

    private void migrateAdmins(MongoDatabase database) {
        var admins = database.getCollection("admins");
        var players = database.getCollection("players");

        List<WriteModel<Document>> updates = new ArrayList<>();

        for (var admin : admins.find()) {
            String uuid = admin.getString("uuid");
            String password = admin.getString("password");
            Boolean confirmed = admin.getBoolean("adminConfirmed", false);

            if (uuid == null) continue;

            updates.add(new UpdateOneModel<>(
                    new Document("uuid", uuid),
                    new Document("$set", new Document()
                            .append("password_hash", password)
                            .append("admin_confirmed", confirmed)
                            .append("is_admin", true)
                    )
            ));

            if (updates.size() >= 1000) {
                players.bulkWrite(updates);
                updates.clear();
            }
        }

        if (!updates.isEmpty()) {
            players.bulkWrite(updates);
        }
    }
}
