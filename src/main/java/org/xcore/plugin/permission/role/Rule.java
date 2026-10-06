package org.xcore.plugin.permission.role;

import org.xcore.plugin.permission.PermissionNode;
import org.xcore.plugin.permission.PermissionNodes;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One line of a role or a direct grant: a node or a wildcard, allowed or denied.
 * <p>
 * A wildcard is {@code *} or {@code a.b.*}. {@code a.*} covers the nodes under {@code a}, not a
 * node named {@code a} itself.
 *
 * @param pattern the node, or the wildcard, without the leading {@code -}
 * @param allow   false for a denial
 */
public record Rule(String pattern, boolean allow) {

    private static final String ANY = "*";
    private static final String WILDCARD_SUFFIX = ".*";
    private static final Pattern PREFIX = Pattern.compile("[a-z0-9-]+(\\.[a-z0-9-]+)*");

    /** The specificity of a rule that names its node exactly; it beats every wildcard. */
    public static final int EXACT = Integer.MAX_VALUE;

    /**
     * Reads a rule as it is written in {@code permissions.toml} or given to a command.
     *
     * @throws IllegalArgumentException when the text is not a rule, names a node nobody declared,
     *                                  or is a wildcard no declared node falls under
     */
    public static Rule parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("An empty permission rule");
        }
        String normalized = text.strip().toLowerCase(Locale.ROOT);
        boolean allow = !normalized.startsWith("-");
        String pattern = allow ? normalized : normalized.substring(1);
        Rule rule = new Rule(pattern, allow);

        if (pattern.equals(ANY)) {
            return rule;
        }
        if (pattern.endsWith(WILDCARD_SUFFIX)) {
            String prefix = pattern.substring(0, pattern.length() - WILDCARD_SUFFIX.length());
            if (!PREFIX.matcher(prefix).matches()) {
                throw new IllegalArgumentException("'" + text + "' is not a permission rule: a wildcard is '*' or 'a.b.*'");
            }
            if (PermissionNodes.all().stream().noneMatch(node -> rule.matches(node.name()))) {
                throw new IllegalArgumentException("'" + text + "' matches no declared permission node");
            }
            return rule;
        }
        if (pattern.contains(ANY)) {
            throw new IllegalArgumentException("'" + text + "' is not a permission rule: a wildcard is '*' or 'a.b.*'");
        }
        PermissionNode declared = PermissionNodes.find(pattern)
                .orElseThrow(() -> new IllegalArgumentException("'" + text + "' is not a declared permission node"));
        return new Rule(declared.name(), allow);
    }

    public boolean isWildcard() {
        return pattern.equals(ANY) || pattern.endsWith(WILDCARD_SUFFIX);
    }

    public boolean matches(String node) {
        if (pattern.equals(ANY)) {
            return true;
        }
        if (pattern.endsWith(WILDCARD_SUFFIX)) {
            // Keeps the dot: "a.b.*" matches "a.b.c" and neither "a.b" nor "a.bc".
            return node.startsWith(pattern.substring(0, pattern.length() - 1));
        }
        return pattern.equals(node);
    }

    /** How narrowly the rule names its nodes: an exact node first, then the longer wildcard. */
    public int specificity() {
        if (pattern.equals(ANY)) {
            return 0;
        }
        if (pattern.endsWith(WILDCARD_SUFFIX)) {
            return (int) pattern.chars().filter(c -> c == '.').count();
        }
        return EXACT;
    }

    @Override
    public String toString() {
        return allow ? pattern : "-" + pattern;
    }
}
