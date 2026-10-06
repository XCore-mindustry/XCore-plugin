package org.xcore.plugin.permission.role;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads {@code permissions.toml}. Unlike the other configs this one is strict: a role that is
 * misspelled or half-read would silently give or take staff rights, so anything unexpected is
 * an error and the caller keeps the model it had.
 */
public final class PermissionModelLoader {

    public static final int SCHEMA_VERSION = 1;

    private static final Pattern ROLE_NAME = Pattern.compile("[a-z0-9][a-z0-9-]*");
    private static final Pattern DISCORD_ID = Pattern.compile("[0-9]{5,25}");
    private static final Set<String> ROOT_KEYS = Set.of("schemaVersion", "roles", "discord");
    private static final Set<String> ROLE_KEYS = Set.of("weight", "parents", "permissions");
    private static final Set<String> DISCORD_KEYS = Set.of("guildId", "bindings");
    private static final Set<String> BINDING_KEYS = Set.of("roleId", "role", "server");

    private PermissionModelLoader() {
    }

    /** @throws PermissionConfigException when the text is not a usable model */
    public static PermissionModel parse(String toml) {
        JsonNode root;
        try {
            root = new TomlMapper().readTree(toml);
        } catch (IOException e) {
            throw new PermissionConfigException("permissions.toml is not valid TOML: " + e.getMessage(), e);
        }
        if (root == null || !root.isObject()) {
            throw new PermissionConfigException("permissions.toml is empty");
        }
        requireKnownKeys(root, ROOT_KEYS, "permissions.toml");

        JsonNode version = root.get("schemaVersion");
        if (version == null || !version.isInt() || version.intValue() != SCHEMA_VERSION) {
            throw new PermissionConfigException("permissions.toml: schemaVersion must be " + SCHEMA_VERSION);
        }

        Map<String, RawRole> raw = readRoles(root.get("roles"));
        Map<String, RoleDefinition> roles = new LinkedHashMap<>();
        for (RawRole role : raw.values()) {
            roles.put(role.name, new RoleDefinition(role.name, role.weight, role.parents, expand(role, raw)));
        }
        return new PermissionModel(SCHEMA_VERSION, roles, readDiscord(root.get("discord"), roles));
    }

    private record RawRole(String name, int weight, List<String> parents, List<Rule> rules) {
    }

    private static Map<String, RawRole> readRoles(JsonNode node) {
        Map<String, RawRole> roles = new LinkedHashMap<>();
        if (node == null) {
            return roles;
        }
        if (!node.isObject()) {
            throw new PermissionConfigException("permissions.toml: 'roles' must be a table");
        }
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            String name = entry.getKey();
            String where = "role '" + name + "'";
            if (!ROLE_NAME.matcher(name).matches()) {
                throw new PermissionConfigException(where + ": a role name is lowercase letters, digits and '-'");
            }
            JsonNode body = entry.getValue();
            if (!body.isObject()) {
                throw new PermissionConfigException(where + " must be a table");
            }
            requireKnownKeys(body, ROLE_KEYS, where);

            JsonNode weight = body.get("weight");
            if (weight == null || !weight.isInt() || weight.intValue() < 0) {
                throw new PermissionConfigException(where + ": weight must be a whole number, 0 or more");
            }

            List<Rule> rules = new ArrayList<>();
            for (String text : strings(body.get("permissions"), where + ": permissions")) {
                try {
                    rules.add(Rule.parse(text));
                } catch (IllegalArgumentException e) {
                    throw new PermissionConfigException(where + ": " + e.getMessage(), e);
                }
            }
            roles.put(name, new RawRole(name, weight.intValue(), strings(body.get("parents"), where + ": parents"), rules));
        }
        for (RawRole role : roles.values()) {
            for (String parent : role.parents) {
                if (!roles.containsKey(parent)) {
                    throw new PermissionConfigException("role '" + role.name + "': unknown parent '" + parent + "'");
                }
            }
        }
        return roles;
    }

    /** The role's own rules, then those of every ancestor, each ancestor once. */
    private static List<Rule> expand(RawRole role, Map<String, RawRole> all) {
        Set<Rule> rules = new LinkedHashSet<>(role.rules);
        collectParents(role, all, new ArrayDeque<>(List.of(role.name)), new HashSet<>(), rules);
        return List.copyOf(rules);
    }

    private static void collectParents(RawRole role, Map<String, RawRole> all, Deque<String> path,
                                       Set<String> done, Set<Rule> rules) {
        for (String parentName : role.parents) {
            if (path.contains(parentName)) {
                List<String> cycle = new ArrayList<>(path);
                java.util.Collections.reverse(cycle);
                cycle.add(parentName);
                throw new PermissionConfigException("roles inherit from each other in a cycle: " + String.join(" -> ", cycle));
            }
            if (!done.add(parentName)) {
                continue;
            }
            RawRole parent = all.get(parentName);
            rules.addAll(parent.rules);
            path.push(parentName);
            collectParents(parent, all, path, done, rules);
            path.pop();
        }
    }

    private static PermissionModel.Discord readDiscord(JsonNode node, Map<String, RoleDefinition> roles) {
        if (node == null) {
            return new PermissionModel.Discord("", List.of());
        }
        if (!node.isObject()) {
            throw new PermissionConfigException("permissions.toml: 'discord' must be a table");
        }
        requireKnownKeys(node, DISCORD_KEYS, "discord");

        String guildId = text(node.get("guildId"), "discord: guildId");
        if (guildId == null || !DISCORD_ID.matcher(guildId).matches()) {
            throw new PermissionConfigException("discord: guildId must be a Discord id written as a string");
        }

        List<PermissionModel.Binding> bindings = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonNode list = node.get("bindings");
        if (list != null) {
            if (!list.isArray()) {
                throw new PermissionConfigException("discord: bindings must be an array of tables");
            }
            for (JsonNode item : list) {
                if (!item.isObject()) {
                    throw new PermissionConfigException("discord: bindings must be an array of tables");
                }
                requireKnownKeys(item, BINDING_KEYS, "discord binding");
                String roleId = text(item.get("roleId"), "discord binding: roleId");
                String role = text(item.get("role"), "discord binding: role");
                String server = text(item.get("server"), "discord binding: server");
                if (roleId == null || !DISCORD_ID.matcher(roleId).matches()) {
                    throw new PermissionConfigException("discord binding: roleId must be a Discord id written as a string");
                }
                if (role == null || !roles.containsKey(role)) {
                    throw new PermissionConfigException("discord binding " + roleId + ": unknown role '" + role + "'");
                }
                if (server != null && server.isBlank()) {
                    throw new PermissionConfigException("discord binding " + roleId + ": server must not be empty");
                }
                if (!seen.add(roleId)) {
                    throw new PermissionConfigException("discord binding " + roleId + " is listed twice");
                }
                bindings.add(new PermissionModel.Binding(roleId, role, server));
            }
        }
        return new PermissionModel.Discord(guildId, bindings);
    }

    private static List<String> strings(JsonNode node, String where) {
        if (node == null) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new PermissionConfigException(where + " must be an array of strings");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.textValue().isBlank()) {
                throw new PermissionConfigException(where + " must be an array of strings");
            }
            values.add(item.textValue().strip());
        }
        return values;
    }

    private static String text(JsonNode node, String where) {
        if (node == null) {
            return null;
        }
        if (!node.isTextual()) {
            throw new PermissionConfigException(where + " must be a string");
        }
        return node.textValue().strip();
    }

    private static void requireKnownKeys(JsonNode node, Set<String> known, String where) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            if (!known.contains(entry.getKey())) {
                throw new PermissionConfigException(where + ": unknown key '" + entry.getKey() + "'");
            }
        }
    }
}
