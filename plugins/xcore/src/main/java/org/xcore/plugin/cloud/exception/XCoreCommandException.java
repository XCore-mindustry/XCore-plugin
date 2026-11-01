package org.xcore.plugin.cloud.exception;

import java.util.Collections;
import java.util.Map;

public class XCoreCommandException extends RuntimeException {
    private final String key;
    private final Map<String, Object> args;
    private final boolean silent;

    public XCoreCommandException(String key) {
        this(key, Collections.emptyMap(), false);
    }

    public XCoreCommandException(String key, Map<String, Object> args) {
        this(key, args, false);
    }

    public XCoreCommandException(boolean silent) {
        this(null, Collections.emptyMap(), silent);
    }

    private XCoreCommandException(String key, Map<String, Object> args, boolean silent) {
        super(key);
        this.key = key;
        this.args = args;
        this.silent = silent;
    }

    public String getKey() {
        return key;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public boolean isSilent() {
        return silent;
    }
}
