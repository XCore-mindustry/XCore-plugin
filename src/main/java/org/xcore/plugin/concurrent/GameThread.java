package org.xcore.plugin.concurrent;

import arc.Core;
import arc.util.Log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single place to ask "am I on the game thread?", plus assertions for the code paths that
 * must be.
 *
 * <p>Mindustry's {@code Player}, {@code Groups}, {@code Vars}, {@code Administration} and
 * {@code Call} are all owned by the tick loop. Touching them from a Redis subscriber, a
 * storage executor, an Arc HTTP worker or a UDP reader is a data race, and the failures
 * show up later as corrupted state rather than as an exception, so they are worth
 * detecting at the point of the mistake rather than debugging afterwards.
 *
 * <p>Two policies are available:
 * <ul>
 *   <li>{@link #require} throws. Use it where there is no correct off-thread behaviour.</li>
 *   <li>{@link #report} logs the first violation for a site and then stays quiet, so a
 *       production server keeps serving instead of dying on a thread assertion. Run with
 *       {@code -Dxcore.strictThreads=true} to make it throw as well; the test suite does
 *       this so a regression fails the build rather than a deployment.</li>
 * </ul>
 */
public final class GameThread {

    /** Set {@code -Dxcore.strictThreads=true} to turn reports into failures. */
    public static final boolean STRICT = Boolean.getBoolean("xcore.strictThreads");

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private GameThread() {
    }

    /**
     * Whether the caller is on the game thread.
     *
     * <p>Before Arc boots there is no game thread to be on, and every storage and
     * configuration path is unit-tested without one, so this reports {@code true} while
     * {@code Core.app} is null rather than failing tests that have nothing to marshal.
     */
    public static boolean isGameThread() {
        var app = Core.app;
        if (app == null) {
            return true;
        }
        return app.isOnMainThread() || Thread.currentThread() == app.getMainThread();
    }

    /** Throws if the caller is not on the game thread. */
    public static void require(String site) {
        if (isGameThread()) {
            return;
        }
        throw new IllegalStateException("Expected the game thread at '" + site + "' but was '"
                + Thread.currentThread().getName() + "'");
    }

    /** Throws if {@code action} would not run on the game thread. */
    public static void require(String site, Runnable action) {
        require(site);
        action.run();
    }

    /**
     * Reports a violation of {@code site} without stopping the caller: the first one is
     * logged with a stack trace, later ones are counted silently. Throws instead when
     * {@link #STRICT} is set.
     */
    public static void report(String site) {
        if (isGameThread()) {
            return;
        }
        if (STRICT) {
            require(site);
        }
        if (REPORTED.add(site)) {
            Log.err("[threads] Off the game thread at '@' on '@'. This races the tick loop.",
                    site, Thread.currentThread().getName());
            Log.err("[threads] Origin: @", new Exception(site));
        }
    }

    /**
     * Runs {@code action} and reports if it was reached off the game thread.
     *
     * <p>The action still runs. Dropping it would hide the symptom behind a feature that
     * quietly stops working, which is harder to diagnose than the race this is meant to
     * surface, so this is a detector rather than a filter.
     */
    public static void report(String site, Runnable action) {
        report(site);
        action.run();
    }

    /** Whether {@code site} has already been reported. Visible for tests. */
    static boolean hasReported(String site) {
        return REPORTED.contains(site);
    }

    /** Clears the reported set. Visible for tests. */
    static void resetReported() {
        REPORTED.clear();
    }
}
