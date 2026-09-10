package org.xcore.plugin.concurrent;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
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
     * Observes an already-native async operation without changing threads.
     * Use this only for thread-safe storage/cache side effects.
     */
    public <T> void observe(CompletionStage<T> stage, AsyncStage.BiResultConsumer<? super T> continuation) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(continuation, "continuation");
        stage.whenComplete(continuation::accept);
    }

    /**
     * Marshals an already-native async operation to the main thread.
     * This is used by reactive drivers so they are not wrapped in a blocking
     * executor or accidentally represented as a nested CompletionStage.
     */
    public <T> void onMain(CompletionStage<T> stage, AsyncStage.BiResultConsumer<? super T> continuation) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(continuation, "continuation");

        stage.whenComplete((value, error) -> {
            try {
                mainThread.execute(() -> continuation.accept(value, error));
            } catch (Throwable dispatchError) {
                // The native stage has already completed; there is no caller
                // future to complete. Keep dispatch failures visible.
                dispatchError.printStackTrace();
            }
        });
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
