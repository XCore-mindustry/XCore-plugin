package org.xcore.plugin.permission.role;

import org.xcore.plugin.permission.Access;
import org.xcore.plugin.permission.PermissionNodes;

import java.util.List;

/**
 * A role of {@code permissions.toml} with its parents already folded in.
 *
 * @param weight used only to decide who may act on whom; it never grants a node
 * @param rules  the role's own rules followed by those of its parents
 */
public record RoleDefinition(String name, int weight, List<String> parents, List<Rule> rules) {

    public RoleDefinition {
        parents = List.copyOf(parents);
        rules = List.copyOf(rules);
    }

    /** Whether holding the role can make somebody staff: it allows at least one staff node. */
    public boolean grantsStaffAccess() {
        return PermissionNodes.all().stream()
                .filter(node -> node.access() == Access.STAFF)
                .anyMatch(node -> {
                    Rule best = null;
                    for (Rule rule : rules) {
                        if (rule.matches(node.name()) && (best == null
                                || rule.specificity() > best.specificity()
                                || rule.specificity() == best.specificity() && !rule.allow())) {
                            best = rule;
                        }
                    }
                    return best != null && best.allow();
                });
    }
}
