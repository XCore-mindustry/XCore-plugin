package org.xcore.plugin.concurrent;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Small fluent facade for asynchronous work that needs a Mindustry callback.
 * The storage operation itself never runs on the game thread.
 */
public final class AsyncStage<T> {
    private final CompletableFuture<T> future;
    private final MainThreadDispatcher mainThread;

    AsyncStage(CompletableFuture<T> future, MainThreadDispatcher mainThread) {
        this.future = Objects.requireNonNull(future, "future");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    /**
     * Runs the successful continuation on Mindustry's main application thread.
     * The returned future completes after the continuation has run.
     */
    public CompletableFuture<Void> thenMain(Consumer<? super T> continuation) {
        Objects.requireNonNull(continuation, "continuation");
        CompletableFuture<Void> result = new CompletableFuture<>();

        future.whenComplete((value, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }

            try {
                mainThread.execute(() -> {
                    try {
                        continuation.accept(value);
                        result.complete(null);
                    } catch (Throwable callbackError) {
                        result.completeExceptionally(callbackError);
                    }
                });
            } catch (Throwable dispatchError) {
                result.completeExceptionally(dispatchError);
            }
        });

        return result;
    }

    /**
     * Runs a continuation on the main thread for either success or failure.
     */
    public CompletableFuture<Void> thenMain(BiResultConsumer<? super T> continuation) {
        Objects.requireNonNull(continuation, "continuation");
        CompletableFuture<Void> result = new CompletableFuture<>();

        future.whenComplete((value, error) -> {
            try {
                mainThread.execute(() -> {
                    try {
                        continuation.accept(value, error);
                        result.complete(null);
                    } catch (Throwable callbackError) {
                        result.completeExceptionally(callbackError);
                    }
                });
            } catch (Throwable dispatchError) {
                result.completeExceptionally(dispatchError);
            }
        });

        return result;
    }

    /** Returns the underlying future for integrations that need standard CF operations. */
    public CompletableFuture<T> future() {
        return future;
    }

    @FunctionalInterface
    public interface BiResultConsumer<T> {
        void accept(T value, Throwable error);
    }
}
