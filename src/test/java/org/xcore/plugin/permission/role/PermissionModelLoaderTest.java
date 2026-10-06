package org.xcore.plugin.permission.role;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionModelLoaderTest {

    static final String SAMPLE = """
            schemaVersion = 1

            [roles.moderator]
            weight = 10
            permissions = [
              "xcore.moderation.mute", "xcore.moderation.unmute", "xcore.moderation.kick",
              "xcore.moderation.audit.others", "xcore.moderation.votekick.immune",
            ]

            [roles.event-organizer]
            weight = 5
            permissions = ["xcore.events.*", "xcore.maps.force-rtv"]

            [roles.admin]
            weight = 50
            parents = ["moderator"]
            permissions = ["xcore.*", "-xcore.permissions.manage", "mindustry.admin"]

            [discord]
            guildId = "123456789012345678"

            [[discord.bindings]]
            roleId = "111111111111111111"
            role = "moderator"

            [[discord.bindings]]
            roleId = "222222222222222222"
            role = "admin"

            [[discord.bindings]]
            roleId = "333333333333333333"
            role = "event-organizer"
            server = "event"
            """;

    @Test
    @DisplayName("The sample from the plan loads: roles, weights, expanded parents and Discord bindings")
    void sample() {
        PermissionModel model = PermissionModelLoader.parse(SAMPLE);

        assertThat(model.roles()).containsOnlyKeys("moderator", "event-organizer", "admin");
        assertThat(model.role("admin").orElseThrow().weight()).isEqualTo(50);
        assertThat(model.role("admin").orElseThrow().rules()).extracting(Rule::toString)
                .startsWith("xcore.*", "-xcore.permissions.manage", "mindustry.admin")
                .contains("xcore.moderation.mute", "xcore.moderation.votekick.immune");
        assertThat(model.discord().guildId()).isEqualTo("123456789012345678");
        assertThat(model.discord().binding("222222222222222222").orElseThrow().role()).isEqualTo("admin");
        assertThat(model.discord().binding("333333333333333333").orElseThrow().server()).isEqualTo("event");
        assertThat(model.discord().binding("999999999999999999")).isEmpty();
    }

    @Test
    @DisplayName("A file without roles or Discord is a valid, empty model")
    void minimal() {
        PermissionModel model = PermissionModelLoader.parse("schemaVersion = 1\n");

        assertThat(model.roles()).isEmpty();
        assertThat(model.discord().bindings()).isEmpty();
    }

    @Test
    @DisplayName("Grandparents are folded in once, however many paths lead to them")
    void diamond() {
        PermissionModel model = PermissionModelLoader.parse("""
                schemaVersion = 1
                [roles.base]
                weight = 1
                permissions = ["xcore.moderation.kick"]
                [roles.left]
                weight = 2
                parents = ["base"]
                permissions = ["xcore.moderation.mute"]
                [roles.right]
                weight = 2
                parents = ["base"]
                [roles.top]
                weight = 3
                parents = ["left", "right"]
                """);

        assertThat(model.role("top").orElseThrow().rules()).extracting(Rule::toString)
                .containsExactly("xcore.moderation.mute", "xcore.moderation.kick");
    }

    private static void rejects(String toml, String... messageParts) {
        assertThatThrownBy(() -> PermissionModelLoader.parse(toml))
                .isInstanceOf(PermissionConfigException.class)
                .hasMessageContainingAll(messageParts);
    }

    @Test
    @DisplayName("A cycle of parents is an error that names the cycle")
    void cycle() {
        rejects("""
                schemaVersion = 1
                [roles.a]
                weight = 1
                parents = ["b"]
                [roles.b]
                weight = 1
                parents = ["c"]
                [roles.c]
                weight = 1
                parents = ["a"]
                """, "cycle", "a -> b -> c -> a");
    }

    @Test
    @DisplayName("An unknown parent, a typo in a node and a wildcard that matches nothing are errors")
    void typos() {
        rejects("""
                schemaVersion = 1
                [roles.a]
                weight = 1
                parents = ["ghost"]
                """, "role 'a'", "unknown parent 'ghost'");
        rejects("""
                schemaVersion = 1
                [roles.a]
                weight = 1
                permissions = ["xcore.moderation.bann"]
                """, "role 'a'", "xcore.moderation.bann", "not a declared permission node");
        rejects("""
                schemaVersion = 1
                [roles.a]
                weight = 1
                permissions = ["xcore.moderaton.*"]
                """, "role 'a'", "matches no declared permission node");
        rejects("""
                schemaVersion = 1
                [roles.a]
                weight = 1
                permissions = ["xcore.*.mute"]
                """, "role 'a'", "a wildcard is '*' or 'a.b.*'");
    }

    @Test
    @DisplayName("A misspelled key is an error rather than a silently ignored setting")
    void unknownKeys() {
        rejects("""
                schemaVersion = 1
                [roles.a]
                weight = 1
                permisions = ["xcore.moderation.mute"]
                """, "role 'a'", "unknown key 'permisions'");
        rejects("""
                schemaVersion = 1
                [role.a]
                weight = 1
                """, "unknown key 'role'");
    }

    @Test
    @DisplayName("A missing weight, a wrong schema version and broken TOML are errors")
    void malformed() {
        rejects("""
                schemaVersion = 1
                [roles.a]
                permissions = []
                """, "role 'a'", "weight");
        rejects("schemaVersion = 2\n", "schemaVersion must be 1");
        rejects("[roles.a]\nweight = 1\n", "schemaVersion must be 1");
        rejects("schemaVersion = \n", "not valid TOML");
    }

    @Test
    @DisplayName("A Discord binding must name a known role and may be listed once")
    void bindings() {
        rejects("""
                schemaVersion = 1
                [discord]
                guildId = "123456789012345678"
                [[discord.bindings]]
                roleId = "111111111111111111"
                role = "ghost"
                """, "unknown role 'ghost'");
        rejects("""
                schemaVersion = 1
                [roles.a]
                weight = 1
                [discord]
                guildId = "123456789012345678"
                [[discord.bindings]]
                roleId = "111111111111111111"
                role = "a"
                [[discord.bindings]]
                roleId = "111111111111111111"
                role = "a"
                """, "listed twice");
        rejects("""
                schemaVersion = 1
                [discord]
                guildId = 123456789012345678
                """, "guildId must be a string");
    }
}
