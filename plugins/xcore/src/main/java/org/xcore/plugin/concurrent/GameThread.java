package org.xcore.plugin.concurrent;

import arc.Core;
import arc.util.Log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

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
 *   <li>{@link #report} counts every violation and logs the first one per site, so a
 *       production server keeps serving instead of dying on a thread assertion. Run with
 *       {@code -Dxcore.strictThreads=true} to make it throw as well; the test suite does
 *       this so a regression fails the build rather than a deployment.</li>
 * </ul>
 *
 * <h2>What the test suite does and does not prove</h2>
 * With no Arc booted there is no game thread to compare against, so {@link #isGameThread}
 * answers {@code true} and the check is a no-op. That is deliberate - most storage and
 * configuration tests never boot Arc, and a detector that fails them for having no engine
 * would be noise - but it means a green strict run only proves the invariant for tests that
 * actually designate a game thread. Tests that care should call
 * {@link #setGameThreadOverride} rather than rely on the default.
 */
public final class GameThread {

    /**
     * The value {@code -Dxcore.strictThreads} was set to at startup. Tests override it
     * through {@link #setStrictOverride} so both policies are covered on either build.
     */
    public static final boolean STRICT_DEFAULT = Boolean.getBoolean("xcore.strictThreads");

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, LongAdder> VIOLATIONS = new ConcurrentHashMap<>();

    private static volatile Boolean strictOverride;
    private static volatile Thread gameThreadOverride;
    private static volatile ViolationListener violationListener;

    private GameThread() {
    }

    /**
     * Notified on every violation, not just the first. Log-once is right for a human
     * reading a log, and wrong for a dashboard: a site firing once at startup and then
     * thousands of times under load looks identical to a site that fired once, unless the
     * repeats are counted somewhere.
     */
    @FunctionalInterface
    public interface ViolationListener {
        void onViolation(String site, String threadName);
    }

    /**
     * Installs the repeat counter sink, normally at startup. Kept as a listener so this
     * class stays free of a dependency on the metrics service.
     */
    public static void setViolationListener(ViolationListener listener) {
        violationListener = listener;
    }

    /**
     * Designates the game thread explicitly. Intended for tests that assert thread affinity
     * without booting Arc, and for the window between construction and Arc's boot. Pass
     * {@code null} to fall back to Arc's own answer.
     */
    public static void setGameThreadOverride(Thread thread) {
        gameThreadOverride = thread;
    }

    /** Whether a {@link #report} violation throws instead of logging. */
    public static boolean isStrict() {
        Boolean override = strictOverride;
        return override != null ? override : STRICT_DEFAULT;
    }

    /** Overrides strict mode for the current JVM. Pass {@code null} to restore the default. */
    static void setStrictOverride(Boolean strict) {
        strictOverride = strict;
    }

    /**
     * Whether the caller is on the game thread.
     *
     * <p>With no Arc booted and no explicit designation there is no game thread to be on, so
     * this reports {@code true}. That keeps every storage and configuration unit test
     * working without an engine, at the cost of the check being unverified there. Callers
     * that need a real answer in tests should set the override.
     */
    public static boolean isGameThread() {
        Thread override = gameThreadOverride;
        if (override != null) {
            return Thread.currentThread() == override;
        }
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
     * Reports a violation of {@code site} without stopping the caller: every violation is
     * counted and handed to the {@linkplain #setViolationListener listener}, and the first
     * one per site is logged with a stack trace. Throws instead when
     * {@link #STRICT_DEFAULT} is set.
     */
    public static void report(String site) {
        if (isGameThread()) {
            return;
        }
        if (isStrict()) {
            require(site);
        }
        recordViolation(site);
        if (REPORTED.add(site)) {
            Log.err("[threads] Off the game thread at '@' on '@'. This races the tick loop.",
                    site, Thread.currentThread().getName());
            Log.err("[threads] Origin: @", new Exception(site));
        }
    }

    private static void recordViolation(String site) {
        VIOLATIONS.computeIfAbsent(site, ignored -> new LongAdder()).increment();
        ViolationListener listener = violationListener;
        if (listener != null) {
            try {
                listener.onViolation(site, Thread.currentThread().getName());
            } catch (RuntimeException | Error ex) {
                // A broken metrics sink must not take down whatever was being diagnosed.
                Log.err("[threads] Violation listener failed for '@'", site, ex);
            }
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

    /** How many times {@code site} has been reported. Visible for tests. */
    static long violationCount(String site) {
        LongAdder counter = VIOLATIONS.get(site);
        return counter == null ? 0L : counter.sum();
    }

    /** Clears the reported set and its counters. Visible for tests. */
    static void resetReported() {
        REPORTED.clear();
        VIOLATIONS.clear();
    }
}
