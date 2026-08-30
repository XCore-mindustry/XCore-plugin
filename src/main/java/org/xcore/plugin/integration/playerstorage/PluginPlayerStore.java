package org.xcore.plugin.integration.playerstorage;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

public interface PluginPlayerStore {
    Optional<PlayerRecord> find(String uuid);
    boolean exists(String uuid);
    /** Returns true when the write is acknowledged and matched or upserted. */
    boolean set(String uuid, String field, Object value);

    /** Returns true when an existing record was matched and updated. */
    boolean remove(String uuid, String field);

    /** Returns true when the write is acknowledged and matched or upserted. */
    boolean increment(String uuid, String field, Number delta);

    /**
     * Applies one idempotent mutation to a player record. The operation is
     * atomic for this player and repeated operation IDs are no-ops.
     */
    boolean applyOnce(String operationId, String uuid, Map<String, Number> increments,
                     Map<String, Object> values);

    /** Returns true only when an existing record was actually deleted. */
    boolean delete(String uuid);
    PlayerPage top(SortField sort, int limit, String cursor);
    OptionalLong rankOf(String uuid, SortField sort);
}
