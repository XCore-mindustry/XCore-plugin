package org.xcore.plugin.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionNodesTest {

    @Test
    @DisplayName("Every node constant is in the catalog")
    void constantsAreDeclared() throws IllegalAccessException {
        List<String> undeclared = new ArrayList<>();
        for (Field field : PermissionNodes.class.getDeclaredFields()) {
            if (!Modifier.isPublic(field.getModifiers()) || field.getType() != String.class) {
                continue;
            }
            String node = (String) field.get(null);
            if (PermissionNodes.find(node).isEmpty()) {
                undeclared.add(field.getName());
            }
        }

        assertThat(undeclared).isEmpty();
    }

    @Test
    @DisplayName("The old 'admin' string still resolves, to the game's admin node")
    void legacyAlias() {
        assertThat(PermissionNodes.find("admin"))
                .map(PermissionNode::name)
                .contains(PermissionNodes.MINDUSTRY_ADMIN);
    }

    @Test
    @DisplayName("A name outside the catalog resolves to nothing")
    void unknown() {
        assertThat(PermissionNodes.find("xcore.moderation.bann")).isEmpty();
        assertThat(PermissionNodes.find("")).isEmpty();
        assertThat(PermissionNodes.find(null)).isEmpty();
    }

    @Test
    @DisplayName("Managing permissions is the only console-only node; nothing is open to every player yet")
    void accessLevels() {
        assertThat(PermissionNodes.all())
                .filteredOn(node -> node.access() == Access.CONSOLE_ONLY)
                .extracting(PermissionNode::name)
                .containsExactly(PermissionNodes.PERMISSIONS_MANAGE);
        assertThat(PermissionNodes.all())
                .filteredOn(node -> node.access() == Access.PLAYER)
                .isEmpty();
    }

    @Test
    @DisplayName("Every node has a description in the default bundle")
    void descriptions() throws IOException {
        String bundle;
        try (var in = getClass().getResourceAsStream("/bundles/bundle_en.ftl")) {
            bundle = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (PermissionNode node : PermissionNodes.all()) {
            assertThat(bundle).contains("\n" + node.descriptionKey() + " = ");
        }
    }
}
