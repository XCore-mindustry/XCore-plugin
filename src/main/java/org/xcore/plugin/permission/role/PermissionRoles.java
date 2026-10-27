package org.xcore.plugin.permission.role;

import arc.files.Fi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.xcore.plugin.common.PLog;
import org.xcore.plugin.config.TomlXcoreConfig;

import java.time.Clock;

/**
 * The role model this server runs with and the switch around it.
 * <p>
 * {@code permissions.toml} lives next to {@code secrets.toml} and is shared by every server. It
 * is changed by deploying it, not by commands; {@link #reload()} only reads it again.
 */
@Singleton
public class PermissionRoles {

    public static final String FILE_NAME = "permissions.toml";

    private final boolean trustNativeAdmins;
    private final String serverName;
    private final Fi file;
    private final Clock clock;
    private volatile PermissionModel model = PermissionModel.EMPTY;

    public PermissionRoles(TomlXcoreConfig config) {
        this(config, Clock.systemUTC());
    }

    @Inject
    public PermissionRoles(TomlXcoreConfig config, Clock clock) {
        this(config.permissions.trustNativeAdmins,
                config.server.name,
                Fi.get(config.paths.globalConfigDirectory == null || config.paths.globalConfigDirectory.isBlank()
                        ? System.getProperty("user.home")
                        : config.paths.globalConfigDirectory).child(FILE_NAME),
                clock);

        // No usable role file would leave the server with no staff at all, or with whatever
        // was half-read. Refuse to start instead.
        this.model = read();
        PLog.infoTag("Permissions", "@ roles from @", model.roles().size(), file.absolutePath());
    }

    /** For tests: no file is read until {@link #reload()}. */
    public PermissionRoles(boolean trustNativeAdmins, String serverName, Fi file, Clock clock) {
        this.trustNativeAdmins = trustNativeAdmins;
        this.serverName = serverName;
        this.file = file;
        this.clock = clock;
    }

    /** A model given directly; for tests. */
    public static PermissionRoles of(PermissionModel model, String serverName, boolean trustNativeAdmins, Clock clock) {
        PermissionRoles roles = new PermissionRoles(trustNativeAdmins, serverName, null, clock);
        roles.model = model;
        return roles;
    }

    public boolean trustNativeAdmins() {
        return trustNativeAdmins;
    }

    public String serverName() {
        return serverName;
    }

    public Clock clock() {
        return clock;
    }

    public PermissionModel model() {
        return model;
    }

    public Fi file() {
        return file;
    }

    /**
     * Reads the file again. On any error the model in use stays as it was.
     *
     * @throws PermissionConfigException with what is wrong with the file
     */
    public PermissionModel reload() {
        PermissionModel loaded = read();
        this.model = loaded;
        return loaded;
    }

    private PermissionModel read() {
        if (file == null || !file.exists()) {
            throw new PermissionConfigException((file == null ? FILE_NAME : file.absolutePath()) + " does not exist");
        }
        return PermissionModelLoader.parse(file.readString("UTF-8"));
    }
}
