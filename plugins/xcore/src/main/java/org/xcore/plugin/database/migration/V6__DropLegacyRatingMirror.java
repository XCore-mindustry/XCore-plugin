package org.xcore.plugin.database.migration;

import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.RenameCollectionOptions;
import jakarta.inject.Singleton;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Retires the pre-ladder storage once {@code rating_standings} is the only source of truth.
 * <ul>
 *   <li>{@code players.pvp_rating/pvp_matches/pvp_wins} were a mirror of the MiniPvP standings and are
 *       unset. {@code legacy_pvp_rating}, the pre-Elo rating shown in the profile, is kept.</li>
 *   <li>The old HexedCore collection is renamed rather than dropped, so an operator can still
 *       inspect or restore it; it is no longer read or written.</li>
 * </ul>
 * Re-running is a no-op.
 */
@Singleton
public class V6__DropLegacyRatingMirror implements Migration {
    static final String HEXED_LEGACY = "xcore_plugin_hexedcore_rating_players";
    static final String HEXED_ARCHIVE = "xcore_plugin_hexedcore_rating_players_legacy";

    @Override
    public int getVersion() {
        return 6;
    }

    @Override
    public String getDescription() {
        return "Unset the players.pvp_* rating mirror and archive the old HexedCore rating collection";
    }

    @Override
    public void up(MongoDatabase database) {
        database.getCollection("players").updateMany(
                new Document("$or", List.of(
                        new Document("pvp_rating", new Document("$exists", true)),
                        new Document("pvp_matches", new Document("$exists", true)),
                        new Document("pvp_wins", new Document("$exists", true)))),
                new Document("$unset", new Document("pvp_rating", "")
                        .append("pvp_matches", "")
                        .append("pvp_wins", "")));

        archiveHexedCollection(database);
    }

    private static void archiveHexedCollection(MongoDatabase database) {
        var names = database.listCollectionNames().into(new ArrayList<>());
        if (!names.contains(HEXED_LEGACY) || names.contains(HEXED_ARCHIVE)) return;
        database.getCollection(HEXED_LEGACY)
                .renameCollection(new MongoNamespace(database.getName(), HEXED_ARCHIVE),
                        new RenameCollectionOptions().dropTarget(false));
    }
}
