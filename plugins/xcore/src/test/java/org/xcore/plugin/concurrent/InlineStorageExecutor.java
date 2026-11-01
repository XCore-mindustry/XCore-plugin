package org.xcore.plugin.concurrent;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.when;

/**
 * A {@link StorageExecutor} that runs each callable on the calling thread and returns an
 * already-completed future.
 *
 * <p>Code that splits work across the storage executor and the game thread can only be
 * asserted deterministically if both legs run inline; the alternative is a sleep or a
 * timeout-based wait, which makes a failing test slow and a passing one flaky.
 *
 * <p>This is for tests only. A real {@link StorageExecutor} is what bounds how much
 * blocking I/O can be in flight.
 */
public final class InlineStorageExecutor {

    private InlineStorageExecutor() {
    }

    public static StorageExecutor create() {
        StorageExecutor executor = Mockito.mock(StorageExecutor.class);
        when(executor.supply(ArgumentMatchers.<Callable<Object>>any())).thenAnswer(invocation -> {
            Callable<Object> task = invocation.getArgument(0);
            try {
                return CompletableFuture.completedFuture(task.call());
            } catch (RuntimeException | Error ex) {
                throw ex;
            } catch (Exception ex) {
                return CompletableFuture.failedFuture(ex);
            }
        });
        return executor;
    }
}
