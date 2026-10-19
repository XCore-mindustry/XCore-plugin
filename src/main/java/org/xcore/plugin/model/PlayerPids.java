package org.xcore.plugin.model;

import org.jspecify.annotations.Nullable;

/**
 * Player PIDs are signed: technical admins hand out zero and negative PIDs to special players
 * (for example event participants), so no ordinary int value can mean "no PID".
 * {@link #NONE} is the one reserved value.
 */
public final class PlayerPids {
    /** Marks a player that has not been assigned a PID yet, or an unknown PID. */
    public static final int NONE = Integer.MIN_VALUE;

    private PlayerPids() {
    }

    public static boolean isAssigned(int pid) {
        return pid != NONE;
    }

    public static boolean isAssigned(@Nullable Integer pid) {
        return pid != null && pid != NONE;
    }

    /** The PID, or {@code null} when it is not assigned. */
    public static @Nullable Integer orNull(@Nullable Integer pid) {
        return isAssigned(pid) ? pid : null;
    }

    /** The PID of the player, or {@link #NONE} when the player is unknown. */
    public static int of(@Nullable PlayerData data) {
        return data == null ? NONE : data.pid;
    }

    /**
     * Parses a PID typed by a player or admin: {@code 12}, {@code -12}, {@code #12} or {@code #-12}.
     *
     * @return the PID, or {@code null} when the text is not a PID
     */
    public static @Nullable Integer parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (!value.matches("-?\\d+")) {
            return null;
        }
        try {
            int pid = Integer.parseInt(value);
            return isAssigned(pid) ? pid : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
