package org.xcore.plugin.service.network;

import java.util.List;
import java.util.Set;

/**
 * Shared Redis transport compatibility surface.
 *
 * <p>This class exists to make the external contract explicit before the transport backend is split into
 * focused components. Downstream refactor steps can depend on these constants instead of re-discovering
 * stream prefixes, envelope field names, or compatibility promises from router conditionals.</p>
 */
public final class RedisTransportContracts {
    public static final String STREAM_PREFIX_EVENT = "xcore:evt:";
    public static final String STREAM_PREFIX_COMMAND = "xcore:cmd:";
    public static final String STREAM_PREFIX_RPC_REQUEST = "xcore:rpc:req:";
    public static final String STREAM_PREFIX_RPC_RESPONSE = "xcore:rpc:resp:";

    public static final String ENVELOPE_SCHEMA_VERSION = "1";

    public static final String FIELD_SCHEMA_VERSION = "schema_version";
    public static final String FIELD_EVENT_TYPE = "event_type";
    public static final String FIELD_EVENT_ID = "event_id";
    public static final String FIELD_IDEMPOTENCY_KEY = "idempotency_key";
    public static final String FIELD_PRODUCER = "producer";
    public static final String FIELD_CREATED_AT = "created_at";
    public static final String FIELD_EXPIRES_AT = "expires_at";
    public static final String FIELD_SERVER = "server";
    public static final String FIELD_PAYLOAD_JSON = "payload_json";

    public static final String FIELD_RPC_TYPE = "rpc_type";
    public static final String FIELD_CORRELATION_ID = "correlation_id";
    public static final String FIELD_REQUEST_ID = "request_id";
    public static final String FIELD_REPLY_TO = "reply_to";
    public static final String FIELD_REQUESTED_BY = "requested_by";
    public static final String FIELD_TIMEOUT_MS = "timeout_ms";

    public static final String FIELD_STATUS = "status";
    public static final String FIELD_ERROR_CODE = "error_code";
    public static final String FIELD_ERROR_MESSAGE = "error_message";
    public static final String FIELD_RESPONDED_AT = "responded_at";

    public static final String FIELD_SOURCE_STREAM = "source_stream";
    public static final String FIELD_SOURCE_GROUP = "source_group";
    public static final String FIELD_SOURCE_ID = "source_id";
    public static final String FIELD_FAILED_AT = "failed_at";
    public static final String FIELD_ATTEMPTS = "attempts";
    public static final String FIELD_FAILURE_REASON = "failure_reason";
    public static final String FIELD_MESSAGE_JSON = "message_json";

    public static final List<String> EVENT_ENVELOPE_FIELDS = List.of(
            FIELD_SCHEMA_VERSION,
            FIELD_EVENT_TYPE,
            FIELD_EVENT_ID,
            FIELD_IDEMPOTENCY_KEY,
            FIELD_PRODUCER,
            FIELD_CREATED_AT,
            FIELD_EXPIRES_AT,
            FIELD_SERVER,
            FIELD_PAYLOAD_JSON
    );

    public static final List<String> RPC_REQUEST_ENVELOPE_FIELDS = List.of(
            FIELD_SCHEMA_VERSION,
            FIELD_RPC_TYPE,
            FIELD_CORRELATION_ID,
            FIELD_REQUEST_ID,
            FIELD_IDEMPOTENCY_KEY,
            FIELD_REPLY_TO,
            FIELD_REQUESTED_BY,
            FIELD_SERVER,
            FIELD_TIMEOUT_MS,
            FIELD_CREATED_AT,
            FIELD_EXPIRES_AT,
            FIELD_PAYLOAD_JSON
    );

    public static final List<String> RPC_RESPONSE_ENVELOPE_FIELDS = List.of(
            FIELD_SCHEMA_VERSION,
            FIELD_RPC_TYPE,
            FIELD_CORRELATION_ID,
            FIELD_SERVER,
            FIELD_STATUS,
            FIELD_ERROR_CODE,
            FIELD_ERROR_MESSAGE,
            FIELD_RESPONDED_AT,
            FIELD_PAYLOAD_JSON
    );

    public static final List<String> DLQ_ENVELOPE_FIELDS = List.of(
            FIELD_SOURCE_STREAM,
            FIELD_SOURCE_GROUP,
            FIELD_SOURCE_ID,
            FIELD_FAILED_AT,
            FIELD_ATTEMPTS,
            FIELD_FAILURE_REASON,
            FIELD_EVENT_TYPE,
            FIELD_RPC_TYPE,
            FIELD_MESSAGE_JSON
    );

    /**
     * Contract strings intentionally preserved for cross-service compatibility.
     */
    public static final Set<String> STABLE_STREAM_PATTERNS = Set.of(
            "xcore:evt:*",
            "xcore:cmd:*",
            "xcore:rpc:req:*",
            "xcore:rpc:resp:*"
    );

    private RedisTransportContracts() {
    }

    public static boolean isStableExternalStream(String streamKey) {
        return streamKey.startsWith(STREAM_PREFIX_EVENT)
                || streamKey.startsWith(STREAM_PREFIX_COMMAND)
                || streamKey.startsWith(STREAM_PREFIX_RPC_REQUEST)
                || streamKey.startsWith(STREAM_PREFIX_RPC_RESPONSE);
    }
}
