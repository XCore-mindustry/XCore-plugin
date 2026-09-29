package org.xcore.plugin.concurrent;

import arc.Application;
import arc.Core;
import mindustry.gen.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // Mirrors the production default: report, do not abort the caller.
        assertFalse(GameThread.STRICT, "this test assumes xcore.strictThreads is not set");

        Thread other = new Thread(() -> {
            GameThread.report("repeated-site");
            GameThread.report("repeated-site");
        }, "redis-sub-test");
        other.start();
        other.join();

        assertTrue(GameThread.hasReported("repeated-site"));
    }

    @Test
    @DisplayName("report still runs the action, so the race is surfaced rather than hidden")
    void report_withAction_stillRunsTheAction() {
        bootWithGameThread(Thread.currentThread());

        AtomicReference<String> ran = new AtomicReference<>();
        GameThread.report("test-site", () -> ran.set("yes"));

        assertEquals("yes", ran.get());
    }

    @Test
    @DisplayName("a player reference from another thread is reported as off the game thread")
    void playerSendFromSubscriberThreadIsDetectable() throws Exception {
        bootWithGameThread(Thread.currentThread());
        Player player = Mockito.mock(Player.class);
        player.con = Mockito.mock(mindustry.net.NetConnection.class);

        Thread other = new Thread(() -> GameThread.report("send-to:" + player, () -> player.sendMessage("hi")), "redis-sub-test");
        other.start();
        other.join();

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

        Thread other = new Thread(() -> GameThread.report("logger-site"), "redis-sub-test");
        other.start();
        other.join();

        assertTrue(GameThread.hasReported("logger-site"));
    }
}
