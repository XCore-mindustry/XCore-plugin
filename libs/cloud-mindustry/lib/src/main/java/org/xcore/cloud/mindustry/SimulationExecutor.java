package org.xcore.cloud.mindustry;

import arc.Application;
import arc.ApplicationListener;
import arc.Core;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * The thread Mindustry's game state may be touched from, and the way onto it.
 * <p>
 * {@link #execute} runs the task at once when called on that thread, so a command issued from the
 * game thread is still handled synchronously; from any other thread it queues the task.
 */
public interface SimulationExecutor extends Executor {

    /**
     * @return whether the calling thread is the simulation thread. False while that thread is
     * not known yet.
     */
    boolean isOnThread();

    /**
     * @throws IllegalStateException when called from any other thread
     */
    default void requireOnThread() {
        if (!isOnThread()) {
            throw new IllegalStateException("Game state was accessed from thread '"
                    + Thread.currentThread().getName() + "', not the simulation thread; go through the manager's simulationExecutor()");
        }
    }

    /**
     * An executor for {@code application}'s main thread, posting to it from other threads. The
     * application is not owned: it is never disposed here. Tasks submitted from another thread
     * after it has been disposed are rejected.
     */
    static SimulationExecutor forApplication(Application application) {
        Objects.requireNonNull(application);

        final class ApplicationExecutor implements SimulationExecutor, ApplicationListener {
            private volatile boolean disposed;

            @Override
            public boolean isOnThread() {
                Thread main = application.getMainThread();
                return main != null && Thread.currentThread() == main;
            }

            @Override
            public void execute(Runnable task) {
                if (isOnThread()) {
                    task.run();
                } else if (disposed) {
                    throw new RejectedExecutionException("The application has been disposed");
                } else {
                    application.post(task);
                }
            }

            @Override
            public void dispose() {
                disposed = true;
            }
        }

        ApplicationExecutor executor = new ApplicationExecutor();
        application.addListener(executor);
        return executor;
    }

    /**
     * An executor for a known thread; {@code dispatcher} has to run the tasks it is given on
     * {@code thread}.
     */
    static SimulationExecutor bound(Thread thread, Executor dispatcher) {
        Objects.requireNonNull(thread);
        Objects.requireNonNull(dispatcher);

        return new SimulationExecutor() {
            @Override
            public boolean isOnThread() {
                return Thread.currentThread() == thread;
            }

            @Override
            public void execute(Runnable task) {
                if (isOnThread()) {
                    task.run();
                } else {
                    dispatcher.execute(task);
                }
            }
        };
    }

    /**
     * The executor of the running application ({@link Core#app}). Without one, as in unit tests,
     * the calling thread is taken as the simulation thread and nothing can be submitted from others.
     */
    static SimulationExecutor forCurrentApplication() {
        if (Core.app != null) {
            return forApplication(Core.app);
        }
        return bound(Thread.currentThread(), task -> {
            throw new RejectedExecutionException("There is no application to post to");
        });
    }
}
