package org.xcore.plugin.model.enums;

public enum AuthResultStatus {
    SUCCESS,
    PASSWORD_CREATED,
    WRONG_PASSWORD,
    PASSWORD_TOO_SHORT,
    DISCORD_APPROVAL_REQUIRED,
    SESSION_NOT_FOUND,
    RATE_LIMITED,
    TOKEN_INVALID
}
