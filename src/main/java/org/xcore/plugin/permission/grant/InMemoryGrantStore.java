package org.xcore.plugin.permission.grant;

import com.mongodb.client.ClientSession;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Non-durable {@link GrantStore} with the semantics of the MongoDB one, for tests. */
public final class InMemoryGrantStore implements GrantStore {

    private final Map<String, GrantDocument> documents = new HashMap<>();
    private RuntimeException failure;

    /** Makes every call fail with {@code failure} until it is set back to null, like a store that is down. */
    public synchronized void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    private void checkAvailable() {
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public synchronized GrantDocument find(String uuid) {
        checkAvailable();
        return documents.getOrDefault(uuid, GrantDocument.empty(uuid));
    }

    @Override
    public synchronized Map<String, GrantDocument> findAll(Collection<String> uuids) {
        checkAvailable();
        Map<String, GrantDocument> found = new HashMap<>();
        for (String uuid : uuids) {
            GrantDocument document = documents.get(uuid);
            if (document != null) {
                found.put(uuid, document);
            }
        }
        return found;
    }

    @Override
    public synchronized Optional<GrantDocument> replace(@Nullable ClientSession session, String uuid,
                                                        long expectedRevision, List<Grant> grants) {
        checkAvailable();
        if (find(uuid).revision() != expectedRevision) {
            return Optional.empty();
        }
        GrantDocument written = new GrantDocument(uuid, expectedRevision + 1, grants);
        documents.put(uuid, written);
        return Optional.of(written);
    }

    @Override
    public synchronized List<String> findUuidsWithExpiredGrants(Instant now) {
        checkAvailable();
        return documents.values().stream()
                .filter(document -> document.grants().stream().anyMatch(grant -> grant.isExpired(now)))
                .map(GrantDocument::uuid)
                .sorted()
                .toList();
    }
}
