package org.xcore.plugin.model;

public class AuthStatusPacket {
    public boolean isDiscordLinked;
    public String discordUsername;
    public boolean hasDiscordAdmin;
    public boolean hasPassword;
    public boolean isAdmin;
    public long revision;

    public AuthStatusPacket(boolean isDiscordLinked, String discordUsername, boolean hasDiscordAdmin, boolean hasPassword, boolean isAdmin, long revision) {
        this.isDiscordLinked = isDiscordLinked;
        this.discordUsername = discordUsername == null ? "" : discordUsername;
        this.hasDiscordAdmin = hasDiscordAdmin;
        this.hasPassword = hasPassword;
        this.isAdmin = isAdmin;
        this.revision = revision;
    }

    public AuthStatusPacket(boolean isDiscordLinked, String discordUsername, boolean hasDiscordAdmin, boolean hasPassword, boolean isAdmin) {
        this(isDiscordLinked, discordUsername, hasDiscordAdmin, hasPassword, isAdmin, System.currentTimeMillis());
    }

    public AuthStatusPacket() {}
}
