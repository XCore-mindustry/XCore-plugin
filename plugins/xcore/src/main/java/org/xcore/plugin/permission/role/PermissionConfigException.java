package org.xcore.plugin.permission.role;

/** {@code permissions.toml} cannot be used as it is written. */
public class PermissionConfigException extends RuntimeException {

    public PermissionConfigException(String message) {
        super(message);
    }

    public PermissionConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
