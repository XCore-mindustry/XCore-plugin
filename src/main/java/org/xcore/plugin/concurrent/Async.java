package org.xcore.plugin.concurrent;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/** Entry point for concise, thread-safe asynchronous plugin work. */
@Singleton
public class Async {
    private final StorageExecutor storageExecutor;
    private final MainThreadDispatcher mainThread;

    @Inject
    public Async(StorageExecutor storageExecutor) {
        this(storageExecutor, MainThreadDispatcher.mindustry());
    }

    Async(StorageExecutor storageExecutor, MainThreadDispatcher mainThread) {
        this.storageExecutor = Objects.requireNonNull(storageExecutor, "storageExecutor");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    /** Starts a fire-and-forget task on the bounded storage executor. */
    public void run(Runnable task) {
        storageExecutor.execute(task);
    }

    /** Starts a background query and returns a fluent main-thread stage. */
    public <T> AsyncStage<T> supply(Callable<T> task) {
        return new AsyncStage<>(storageExecutor.supply(task), mainThread);
    }

    /**
     * Starts a player-scoped query. The callback is dispatched to the main
     * thread only while the same player is still connected and in Groups.player.
     */
    public <T> void forPlayer(Player player, Callable<T> task, BiConsumer<Player, T> continuation) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(continuation, "continuation");

        storageExecutor.supply(task).whenComplete((value, error) -> {
            if (error != null) {
                return;
            }

            mainThread.execute(() -> {
                if (player.con == null || !player.con.isConnected() || !Groups.player.contains(candidate -> candidate == player)) {
                    return;
                }
                continuation.accept(player, value);
            });
        });
    }
}
