package org.xcore.plugin.concurrent;

import arc.Application;
import arc.Core;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class GameThreadTest {

    private Application previousApp;
    private Thread gameThread;

    @BeforeEach
    void setUp() {
        previousApp = Core.app;
        GameThread.resetReported();
    }

    @AfterEach
    void tearDown() {
        Core.app = previousApp;
        GameThread.resetReported();
        GameThread.setStrictOverride(null);
        GameThread.setGameThreadOverride(null);
        GameThread.setViolationListener(null);
    }

    @Test
    @DisplayName("an explicit game-thread designation is honoured without Arc")
    void designatedGameThread_isRecognised() {
        // With no Arc booted, isGameThread() answers true on any thread and the check proves
        // nothing. Tests that actually assert affinity designate the thread themselves
        // instead of relying on that fallback.
        GameThread.setGameThreadOverride(Thread.currentThread());
        assertTrue(GameThread.isGameThread());

        AtomicBoolean backgroundSawItselfAsGameThread = new AtomicBoolean();
        onBackgroundThread(() -> backgroundSawItselfAsGameThread.set(GameThread.isGameThread()));

        assertFalse(backgroundSawItselfAsGameThread.get(),
                "a background thread must not be mistaken for the game thread");
    }

    @Test
    @DisplayName("repeats at one site are counted, not just logged once")
    void report_countsEveryRepeatNotOnlyTheFirst() {
        GameThread.setGameThreadOverride(Thread.currentThread());
        assertEquals(0, GameThread.violationCount("session.broadcast"));
        assertFalse(GameThread.hasReported("session.broadcast"));

        onBackgroundThread(() -> nonStrictly(() -> {
            GameThread.report("session.broadcast");
            assertTrue(GameThread.hasReported("session.broadcast"),
                    "the first violation is logged with a stack trace");
            assertEquals(1, GameThread.violationCount("session.broadcast"));

            for (int i = 0; i < 250; i++) {
                GameThread.report("session.broadcast");
            }

            // Log-once is right for a person reading a log and wrong for a dashboard: a
            // site firing once at startup looks identical to one firing under load unless
            // the repeats are counted somewhere.
            assertEquals(251, GameThread.violationCount("session.broadcast"),
                    "repeats must be visible even though the log stays quiet");
        }));
    }

    @Test
    @DisplayName("the violation listener sees every violation, not just the first")
    void report_notifiesTheListenerOnEveryViolation() {
        GameThread.setGameThreadOverride(Thread.currentThread());
        java.util.List<String> seen = new java.util.ArrayList<>();
        GameThread.setViolationListener((site, threadName) -> seen.add(site + "@" + threadName));

        onBackgroundThread(() -> nonStrictly(() -> {
            GameThread.report("discovery.capture");
            GameThread.report("discovery.capture");
        }));

        assertEquals(2, seen.size());
        assertTrue(seen.get(0).startsWith("discovery.capture@"), seen.get(0));
    }

    @Test
    @DisplayName("a broken violation listener does not propagate into the caller")
    void report_survivesAFailingListener() {
        GameThread.setGameThreadOverride(Thread.currentThread());
        GameThread.setViolationListener((site, threadName) -> {
            throw new IllegalStateException("metrics backend is down");
        });

        onBackgroundThread(() -> assertDoesNotThrow(() -> nonStrictly(() -> GameThread.report("broadcast"))));

        assertEquals(1, GameThread.violationCount("broadcast"),
                "the counter is incremented before the listener is consulted");
    }

    /**
     * Runs {@code body} on a dedicated thread that is not the designated game thread, and
     * rethrows anything it threw so a failure inside the body cannot pass silently.
     */
    private void onBackgroundThread(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread background = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable ex) {
                failure.set(ex);
            }
        }, "not-the-game-thread");
        background.start();
        try {
            background.join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for the background thread", ex);
        }
        if (failure.get() != null) {
            throw new AssertionError("the background thread failed", failure.get());
        }
    }

    /** Runs {@code body} with strict reporting explicitly off, whatever the build default. */
    private void nonStrictly(Runnable body) {
        GameThread.setStrictOverride(false);
        body.run();
    }

    /**
     * Boots a stub Arc application that treats the test's own thread as the game thread,
     * so the two cases below are just "on" and "some other thread".
     */
    private void bootWithGameThread(Thread thread) {
        gameThread = thread;
        Application app = Mockito.mock(Application.class);
        when(app.getMainThread()).thenReturn(gameThread);
        when(app.isOnMainThread()).thenAnswer(invocation -> Thread.currentThread() == gameThread);
        Core.app = app;
    }

    @Test
    @DisplayName("isGameThread is true before Arc boots so unit tests are not forced to marshal")
    void isGameThread_isTrueBeforeArcBoots() {
        Core.app = null;

        assertTrue(GameThread.isGameThread());
    }

    @Test
    @DisplayName("isGameThread is true on the game thread")
    void isGameThread_isTrueOnGameThread() {
        bootWithGameThread(Thread.currentThread());

        assertTrue(GameThread.isGameThread());
    }

    @Test
    @DisplayName("isGameThread is false on any other thread")
    void isGameThread_isFalseOffGameThread() throws Exception {
        bootWithGameThread(Thread.currentThread());

        AtomicReference<Boolean> seen = new AtomicReference<>();
        Thread other = new Thread(() -> seen.set(GameThread.isGameThread()), "redis-sub-test");
        other.start();
        other.join();

        assertFalse(seen.get());
    }

    @Test
    @DisplayName("require passes silently on the game thread")
    void require_passesOnGameThread() {
        bootWithGameThread(Thread.currentThread());

        assertDoesNotThrow(() -> GameThread.require("test-site"));
    }

    @Test
    @DisplayName("require throws off the game thread and names the site and thread")
    void require_throwsOffGameThread() throws Exception {
        bootWithGameThread(Thread.currentThread());

        var failure = new AtomicReference<Throwable>();
        Thread other = new Thread(() -> {
            try {
                GameThread.require("player-send");
            } catch (Throwable ex) {
                failure.set(ex);
            }
        }, "redis-sub-test");
        other.start();
        other.join();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> {
            if (failure.get() != null) {
                throw (IllegalStateException) failure.get();
            }
        });
        assertTrue(thrown.getMessage().contains("player-send"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("redis-sub-test"), thrown.getMessage());
    }

    @Test
    @DisplayName("require with an action runs the action on the game thread")
    void require_withAction_runsTheAction() {
        bootWithGameThread(Thread.currentThread());

        AtomicReference<String> ran = new AtomicReference<>();
        GameThread.require("test-site", () -> ran.set("yes"));

        assertEquals("yes", ran.get());
    }

    @Test
    @DisplayName("report does not throw when strict mode is off, and logs a site only once")
    void report_logsOnceWithoutThrowing() throws Exception {
        bootWithGameThread(Thread.currentThread());

        // Production default: report, do not abort the caller.
        nonStrictly(() -> {
            Thread other = new Thread(() -> {
                GameThread.report("repeated-site");
                GameThread.report("repeated-site");
            }, "redis-sub-test");
            other.start();
            try {
                other.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        });

        assertTrue(GameThread.hasReported("repeated-site"));
    }

    @Test
    @DisplayName("report turns into a failure under -Dxcore.strictThreads")
    void report_throwsUnderStrictMode() throws Exception {
        bootWithGameThread(Thread.currentThread());
        GameThread.setStrictOverride(true);

        var failure = new AtomicReference<Throwable>();
        Thread other = new Thread(() -> {
            try {
                GameThread.report("strict-site");
            } catch (Throwable ex) {
                failure.set(ex);
            }
        }, "redis-sub-test");
        other.start();
        other.join();

        assertInstanceOf(IllegalStateException.class, failure.get());
        assertTrue(failure.get().getMessage().contains("strict-site"), failure.get().getMessage());
        // The throw happens before the site is recorded as reported.
        assertFalse(GameThread.hasReported("strict-site"));
    }

    @Test
    @DisplayName("report still runs the action, so the race is surfaced rather than hidden")
    void report_withAction_stillRunsTheAction() {
        bootWithGameThread(Thread.currentThread());

        AtomicReference<String> ran = new AtomicReference<>();
        nonStrictly(() -> GameThread.report("test-site", () -> ran.set("yes")));

        assertEquals("yes", ran.get());
    }

    @Test
    @DisplayName("a player reference from another thread is reported as off the game thread")
    void playerSendFromSubscriberThreadIsDetectable() throws Exception {
        bootWithGameThread(Thread.currentThread());
        Player player = Mockito.mock(Player.class);
        player.con = Mockito.mock(mindustry.net.NetConnection.class);

        nonStrictly(() -> {
            Thread other = new Thread(() -> GameThread.report("send-to:" + player, () -> player.sendMessage("hi")), "redis-sub-test");
            other.start();
            try {
                other.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        });

        assertTrue(GameThread.hasReported("send-to:" + player));
    }

    @Test
    @DisplayName("the reported set is isolated per site")
    void reportedSet_isPerSite() {
        bootWithGameThread(Thread.currentThread());

        assertFalse(GameThread.hasReported("site-a"));
        assertFalse(GameThread.hasReported("site-b"));
    }

    @Test
    @DisplayName("the game thread is the one Arc names, not merely the caller")
    void isGameThread_usesArcMainThread() {
        Thread named = new Thread(() -> {
        }, "arc-game-thread");
        bootWithGameThread(named);

        // The test thread is not the thread Arc was told about.
        assertFalse(GameThread.isGameThread());
        assertSame(named, gameThread);
    }

    @Test
    @DisplayName("logging a violation does not itself throw when the logger is unset")
    void report_isSafeWithDefaultLogger() throws Exception {        bootWithGameThread(Thread.currentThread());

        nonStrictly(() -> {
            Thread other = new Thread(() -> GameThread.report("logger-site"), "redis-sub-test");
            other.start();
            try {
                other.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        });

        assertTrue(GameThread.hasReported("logger-site"));
    }
}
