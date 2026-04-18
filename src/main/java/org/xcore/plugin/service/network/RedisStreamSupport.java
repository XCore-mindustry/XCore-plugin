package org.xcore.plugin.service.network;

import io.lettuce.core.XAddArgs;
import io.lettuce.core.XGroupCreateArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.inject.Singleton;
import org.xcore.plugin.config.Config;

import java.util.Map;

@Singleton
final class RedisStreamSupport {
    private static final long MAXLEN_EVT = 50_000L;
    private static final long MAXLEN_CMD = 10_000L;
    private static final long MAXLEN_RPC_REQ = 5_000L;
    private static final long MAXLEN_RPC_RESP = 20_000L;
    private static final long MAXLEN_DLQ = 100_000L;

    private final Config config;

    RedisStreamSupport(Config config) {
        this.config = config;
    }

    String groupFor(Class<?> type, String stream) {
        return config.redisGroupPrefix
                + ":"
                + config.server
                + ":"
                + type.getSimpleName().toLowerCase()
                + ":"
                + Math.abs(stream.hashCode());
    }

    void ensureGroup(RedisCommands<String, String> commands, String stream, String group) {
        try {
            commands.xgroupCreate(XReadArgs.StreamOffset.from(stream, "0-0"), group, new XGroupCreateArgs().mkstream(true));
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || !msg.toUpperCase().contains("BUSYGROUP")) {
                throw e;
            }
        }
    }

    void xaddWithTrim(RedisCommands<String, String> commands, String stream, Map<String, String> fields) {
        commands.xadd(
                stream,
                XAddArgs.Builder.maxlen(streamMaxLen(stream)).approximateTrimming(true),
                fields
        );
    }

    long streamMaxLen(String stream) {
        if (stream.startsWith("xcore:evt:")) {
            return MAXLEN_EVT;
        }
        if (stream.startsWith("xcore:cmd:")) {
            return MAXLEN_CMD;
        }
        if (stream.startsWith("xcore:rpc:req:")) {
            return MAXLEN_RPC_REQ;
        }
        if (stream.startsWith("xcore:rpc:resp:")) {
            return MAXLEN_RPC_RESP;
        }
        if (stream.startsWith(config.redisDlqPrefix + ":")) {
            return MAXLEN_DLQ;
        }
        return MAXLEN_EVT;
    }

    String dlqStreamFor(String sourceStream) {
        if (sourceStream.startsWith("xcore:rpc:")) {
            return config.redisDlqPrefix + ":rpc";
        }
        if (sourceStream.startsWith("xcore:cmd:")) {
            return config.redisDlqPrefix + ":cmd";
        }
        return config.redisDlqPrefix + ":evt";
    }

    String failureKey(String stream, String messageId) {
        return stream + "|" + messageId;
    }

    boolean isNoGroupError(Exception e) {
        String msg = e.getMessage();
        return msg != null && msg.toUpperCase().contains("NOGROUP");
    }
}
