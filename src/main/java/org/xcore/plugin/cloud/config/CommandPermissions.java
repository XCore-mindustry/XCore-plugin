package org.xcore.plugin.cloud.config;

import org.incendo.cloud.Command;
import org.incendo.cloud.permission.Permission;
import org.xcore.plugin.permission.Access;
import org.xcore.plugin.permission.PermissionNodes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the permission nodes a command asks for.
 */
public final class CommandPermissions {

    private CommandPermissions() {
    }

    /** The nodes named anywhere in {@code permission}, however it is combined. */
    public static Set<String> nodes(Permission permission) {
        Set<String> nodes = new LinkedHashSet<>();
        collect(permission, nodes);
        return nodes;
    }

    private static void collect(Permission permission, Set<String> nodes) {
        if (permission == null || permission.isEmpty()) {
            return;
        }
        Collection<Permission> inner = permission.permissions();
        if (inner.isEmpty() || (inner.size() == 1 && inner.contains(permission))) {
            nodes.add(permission.permissionString());
            return;
        }
        for (Permission part : inner) {
            collect(part, nodes);
        }
    }

    /** Whether the command asks for anything an ordinary player does not have. */
    public static boolean isRestricted(Command<?> command) {
        for (String node : nodes(command.commandPermission())) {
            if (PermissionNodes.find(node).map(declared -> declared.access() != Access.PLAYER).orElse(true)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stops startup when a command asks for a node the catalog does not declare. Such a command
     * could never run, and the usual cause is a typo that would otherwise go unnoticed.
     */
    public static void verifyDeclared(Collection<? extends Command<?>> commands) {
        List<String> problems = new ArrayList<>();
        for (Command<?> command : commands) {
            for (String node : nodes(command.commandPermission())) {
                if (PermissionNodes.find(node).isEmpty()) {
                    problems.add("'" + node + "' on /" + command.rootComponent().name());
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Commands ask for undeclared permission nodes: " + String.join(", ", problems));
        }
    }
}
