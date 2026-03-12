package org.xcore.plugin.database.repository;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Updates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;
import org.xcore.plugin.config.GlobalConfig;
import org.xcore.plugin.model.DiscordLinkCode;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

@Singleton
public class DiscordLinkCodeRepository extends DataRepository<DiscordLinkCode> {

    @Inject
    public DiscordLinkCodeRepository(MongoDatabase database, GlobalConfig globalConfig) {
        super(database, "discord_link_codes", DiscordLinkCode.class, globalConfig);

        collection.createIndex(new Document("code", 1), new IndexOptions().unique(true));
        collection.createIndex(new Document("player_uuid", 1));
        collection.createIndex(new Document("expires_at", 1));
        collection.createIndex(new Document("status", 1));
    }

    public DiscordLinkCode findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }

        return collection.find(eq("code", code)).first();
    }

    public List<DiscordLinkCode> findPendingByPlayerUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return List.of();
        }

        return collection.find(Filters.and(
                eq("player_uuid", playerUuid),
                eq("status", "pending")
        )).into(new ArrayList<>());
    }

    public boolean invalidatePendingByPlayerUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank() || isReadOnly()) {
            return false;
        }

        return collection.updateMany(
                Filters.and(eq("player_uuid", playerUuid), eq("status", "pending")),
                Updates.combine(
                        Updates.set("status", "cancelled"),
                        Updates.set("updated_at", System.currentTimeMillis())
                )
        ).getMatchedCount() > 0;
    }

    public boolean consumeCode(String code, String discordId, long consumedAt) {
        if (code == null || code.isBlank() || isReadOnly()) {
            return false;
        }

        return collection.updateOne(
                Filters.and(eq("code", code), eq("status", "pending")),
                Updates.combine(
                        Updates.set("status", "consumed"),
                        Updates.set("consumed_at", consumedAt),
                        Updates.set("consumed_by_discord_id", discordId == null ? "" : discordId),
                        Updates.set("updated_at", System.currentTimeMillis())
                )
        ).getMatchedCount() > 0;
    }

    public boolean expireCode(String code) {
        if (code == null || code.isBlank() || isReadOnly()) {
            return false;
        }

        return collection.updateOne(
                Filters.and(eq("code", code), eq("status", "pending")),
                Updates.combine(
                        Updates.set("status", "expired"),
                        Updates.set("updated_at", System.currentTimeMillis())
                )
        ).getMatchedCount() > 0;
    }
}
