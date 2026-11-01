package org.xcore.plugin.permission.role;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The contents of {@code permissions.toml}: the roles and how Discord roles map onto them.
 * Immutable; a reload builds a new one.
 */
public record PermissionModel(int schemaVersion, Map<String, RoleDefinition> roles, Discord discord) {

    public static final PermissionModel EMPTY = new PermissionModel(1, Map.of(), new Discord("", List.of()));

    public PermissionModel {
        roles = Map.copyOf(roles);
    }

    public Optional<RoleDefinition> role(String name) {
        return Optional.ofNullable(name == null ? null : roles.get(name));
    }

    /**
     * @param guildId  the Discord guild the bindings belong to; empty when Discord is not used
     * @param bindings which Discord role gives which game role
     */
    public record Discord(String guildId, List<Binding> bindings) {

        public Discord {
            bindings = List.copyOf(bindings);
        }

        public Optional<Binding> binding(String discordRoleId) {
            return bindings.stream().filter(binding -> binding.roleId().equals(discordRoleId)).findFirst();
        }
    }

    /**
     * @param roleId the Discord role
     * @param role   the game role it gives
     * @param server the only server the role is given on, or null for all of them
     */
    public record Binding(String roleId, String role, String server) {
    }
}
