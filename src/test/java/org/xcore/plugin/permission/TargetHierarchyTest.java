package org.xcore.plugin.permission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The hierarchy for targets whose roles have to be read: nothing is read on the game thread. */
class TargetHierarchyTest {

    private static final String MUTE = PermissionNodes.MODERATION_MUTE;

    private final RolesWorld world = new RolesWorld();
    private final List<Runnable> storage = new ArrayList<>();
    private final List<String> outcome = new ArrayList<>();
    private TargetHierarchy hierarchy;
    private Session moderator;

    @BeforeEach
    void setUp() {
        // Queues rather than runs, so a test can tell a deferred read from one that happened.
        StorageExecutor executor = mock(StorageExecutor.class);
        when(executor.supply(any())).thenAnswer(call -> {
            Callable<Object> task = call.getArgument(0);
            CompletableFuture<Object> future = new CompletableFuture<>();
            storage.add(() -> {
                try {
                    future.complete(task.call());
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        });
        hierarchy = new TargetHierarchy(world.permissions, world.grants, () -> world.sessionService,
                new Async(executor, world.main::execute));

        world.grants.addRole(RolesWorld.data("mod"), "moderator", null, null, "r", Actor.LOCAL_CONSOLE);
        world.grants.addRole(RolesWorld.data("admin"), "admin", null, null, "r", Actor.LOCAL_CONSOLE);
        moderator = world.join("mod");
        world.staff.logIn(moderator);
    }

    private void ask(String targetUuid) {
        hierarchy.whenAllowed(moderator, targetUuid, MUTE, () -> outcome.add("allowed"), () -> outcome.add("denied"));
    }

    private void readStore() {
        List<Runnable> batch = new ArrayList<>(storage);
        storage.clear();
        batch.forEach(Runnable::run);
        world.main.runQueued();
    }

    @Test
    @DisplayName("A target who is online is decided at once, without the store")
    void online() {
        world.join("admin");
        world.join("player");

        ask("admin");
        ask("player");
        ask("mod");

        assertThat(storage).isEmpty();
        assertThat(outcome).containsExactly("denied", "allowed", "allowed");
    }

    @Test
    @DisplayName("A target who is offline is decided after the store was read, back on the game thread")
    void offline() {
        ask("admin");
        ask("player");

        assertThat(outcome).as("nothing was read on the game thread").isEmpty();
        assertThat(storage).hasSize(2);

        readStore();
        assertThat(outcome).containsExactly("denied", "allowed");
    }

    @Test
    @DisplayName("Finding out who the target is happens off the game thread too, and nobody found leaves it to the action")
    void lookup() {
        hierarchy.whenAllowed(moderator, () -> "admin", MUTE, () -> outcome.add("allowed"), () -> outcome.add("denied"));
        hierarchy.whenAllowed(moderator, () -> null, MUTE, () -> outcome.add("allowed"), () -> outcome.add("denied"));
        assertThat(outcome).isEmpty();

        readStore();
        assertThat(outcome).containsExactly("denied", "allowed");
    }

    @Test
    @DisplayName("The actor lost the permission while the store was read: denied")
    void permissionLostMeanwhile() {
        ask("player");
        world.staff.logOut(moderator);

        readStore();
        assertThat(outcome).containsExactly("denied");
    }

    @Test
    @DisplayName("The actor left while the store was read: nothing happens")
    void actorLeftMeanwhile() {
        ask("player");
        world.leave("mod");

        readStore();
        assertThat(outcome).isEmpty();
    }

    @Test
    @DisplayName("The target joined while the store was read: what they hold here counts")
    void targetJoinedMeanwhile() {
        ask("native");
        RolesWorld trusting = new RolesWorld("main", world.clock, world.store, true);
        Session nativeAdmin = trusting.join("native", true);
        world.online.put("native", nativeAdmin);

        readStore();
        assertThat(outcome).as("an admin by the game's list weighs nothing in the store").containsExactly("denied");
    }

    @Test
    @DisplayName("The store could not be read: denied")
    void storeDown() {
        ask("player");
        world.store.failWith(new IllegalStateException("mongo is down"));

        readStore();
        assertThat(outcome).containsExactly("denied");
    }

    @Test
    @DisplayName("Without roles there is no hierarchy and nothing is read")
    void legacy() {
        TargetHierarchy.none().whenAllowed(moderator, "admin", MUTE, () -> outcome.add("allowed"), () -> outcome.add("denied"));
        TargetHierarchy.none().whenAllowed(moderator, () -> "admin", MUTE, () -> outcome.add("allowed"), () -> outcome.add("denied"));

        assertThat(outcome).containsExactly("allowed", "allowed");
    }
}
