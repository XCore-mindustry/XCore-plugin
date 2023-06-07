package org.xcore.plugin.utils.database;

import arc.struct.Seq;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.xcore.plugin.utils.models.PunishmentHistory;

public class PunishmentHistoryExecutor {
    private final MongoCollection<PunishmentHistory> collection;

    public PunishmentHistoryExecutor(MongoCollection<PunishmentHistory> collection) {
        this.collection = collection;
    }

    public Seq<PunishmentHistory> getPunishments(int pid) {
        return Seq.with(collection.find(Filters.eq("_id", pid)).sort(Sorts.descending("date")));
    }

    public void insertPunishment(PunishmentHistory history) {
        collection.insertOne(history);
    }
}
