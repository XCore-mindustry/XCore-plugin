package org.xcore.plugin.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Every permission node the plugin knows. A node that is not declared here is denied to
 * everyone, the console included, and a command that asks for one stops the server at startup.
 */
public final class PermissionNodes {

    /**
     * Puts a constant of this class into the catalog. The names stay plain literals because
     * {@code @Permission} on a command only accepts a compile-time constant.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface Declared {
        Access value() default Access.STAFF;
    }

    private static final Pattern NAME = Pattern.compile("[a-z0-9-]+(\\.[a-z0-9-]+)+");
    private static final Map<String, PermissionNode> CATALOG = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    /** The built-in admin actions of the game client: skipping waves and opening the admin menu. */
    @Declared public static final String MINDUSTRY_ADMIN = "mindustry.admin";

    @Declared public static final String MODERATION_MUTE = "xcore.moderation.mute";
    @Declared public static final String MODERATION_UNMUTE = "xcore.moderation.unmute";
    @Declared public static final String MODERATION_KICK = "xcore.moderation.kick";
    @Declared public static final String MODERATION_BAN = "xcore.moderation.ban";
    @Declared public static final String MODERATION_UNBAN = "xcore.moderation.unban";
    @Declared public static final String MODERATION_AUDIT_OTHERS = "xcore.moderation.audit.others";
    @Declared public static final String MODERATION_VOTEKICK_IMMUNE = "xcore.moderation.votekick.immune";

    @Declared public static final String ADMIN_TP = "xcore.admin.tp";
    @Declared public static final String ADMIN_BROADCAST = "xcore.admin.broadcast";
    @Declared public static final String ADMIN_KILL = "xcore.admin.kill";
    @Declared public static final String ADMIN_HEAL = "xcore.admin.heal";
    @Declared public static final String ADMIN_SET_TEAM = "xcore.admin.set-team";

    @Declared public static final String MAPS_FORCE_RTV = "xcore.maps.force-rtv";
    @Declared public static final String MAPS_FORCE_VNW = "xcore.maps.force-vnw";
    @Declared public static final String VOTES_CANCEL = "xcore.votes.cancel";

    @Declared public static final String EVENTS_CREATE_MAJOR = "xcore.events.create-major";
    @Declared public static final String EVENTS_EDIT_OTHERS = "xcore.events.edit-others";
    @Declared public static final String EVENTS_FORCE_VOTE = "xcore.events.force-vote";
    @Declared public static final String EVENTS_STOP = "xcore.events.stop";

    @Declared public static final String PLAYERS_SETTINGS_OTHERS = "xcore.players.settings.others";
    @Declared public static final String PLAYERS_PRIVATE_INFO = "xcore.players.private-info";

    @Declared public static final String BYPASS_PLAYTIME = "xcore.bypass.playtime";

    @Declared public static final String PERMISSIONS_INSPECT = "xcore.permissions.inspect";
    @Declared(Access.CONSOLE_ONLY) public static final String PERMISSIONS_MANAGE = "xcore.permissions.manage";

    /** What commands asked for before the catalog existed. Resolves to {@link #MINDUSTRY_ADMIN}. */
    public static final String LEGACY_ADMIN = "admin";

    private PermissionNodes() {
    }

    static {
        for (Field field : PermissionNodes.class.getDeclaredFields()) {
            Declared declared = field.getAnnotation(Declared.class);
            if (declared == null) {
                continue;
            }
            try {
                declare((String) field.get(null), declared.value());
            } catch (IllegalAccessException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
        ALIASES.put(LEGACY_ADMIN, MINDUSTRY_ADMIN);
    }

    private static void declare(String name, Access access) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Malformed permission node: " + name);
        }
        String descriptionKey = "permission-" + name.replace('.', '-');
        if (CATALOG.putIfAbsent(name, new PermissionNode(name, access, descriptionKey)) != null) {
            throw new IllegalArgumentException("Permission node declared twice: " + name);
        }
    }

    /**
     * @return the declared node behind {@code name}, which may be an alias
     */
    public static Optional<PermissionNode> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(CATALOG.get(ALIASES.getOrDefault(name, name)));
    }

    public static Collection<PermissionNode> all() {
        return Collections.unmodifiableCollection(CATALOG.values());
    }
}
