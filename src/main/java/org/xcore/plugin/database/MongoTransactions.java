package org.xcore.plugin.database;

import arc.util.Log;
import com.mongodb.MongoClientException;
import com.mongodb.MongoCommandException;
import com.mongodb.MongoException;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Runs several writes as one transaction where MongoDB allows it. A standalone instance has no
 * transactions; there the body runs with a null session and its writes are not atomic.
 */
@Singleton
public class MongoTransactions {

    private static final int ILLEGAL_OPERATION = 20;

    private final @Nullable MongoClient client;

    @Inject
    public MongoTransactions(@Nullable MongoClient client) {
        this.client = client;
    }

    /** Without a client every body runs with a null session; for tests and in-memory stores. */
    public static MongoTransactions none() {
        return new MongoTransactions(null);
    }

    /**
     * @param body receives the session to pass to every write, or null when there is no
     *             transaction; it may run more than once if the transaction is retried
     */
    public <T> T run(Function<@Nullable ClientSession, T> body) {
        if (client == null) {
            return body.apply(null);
        }
        try (ClientSession session = client.startSession()) {
            return session.withTransaction(() -> body.apply(session));
        } catch (MongoException e) {
            if (!isUnsupported(e)) {
                throw e;
            }
            Log.warn("[Mongo] Transactions are unavailable (standalone instance?); writing without one: @", e.getMessage());
            return body.apply(null);
        }
    }

    private static boolean isUnsupported(MongoException e) {
        if (e instanceof MongoCommandException command && command.getErrorCode() == ILLEGAL_OPERATION) {
            return true;
        }
        if (e instanceof MongoClientException) {
            return true;
        }
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("standalone")
                || lower.contains("transaction numbers are only allowed on a replica set member or mongos")
                || lower.contains("sessions are not supported");
    }
}
