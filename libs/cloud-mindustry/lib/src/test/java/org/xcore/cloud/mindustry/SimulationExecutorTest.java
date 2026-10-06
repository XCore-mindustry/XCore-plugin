package org.xcore.cloud.mindustry;

import arc.Application;
import arc.ApplicationListener;
import arc.struct.Seq;
import arc.util.CommandHandler;
import mindustry.Vars;
import mindustry.core.ContentLoader;
import mindustry.core.GameState;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Player;
import org.incendo.cloud.SenderMapper;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.cloud.mindustry.selector.TargetSelector.SinglePlayerSelector;
import org.xcore.cloud.mindustry.selector.TargetSelectorSpec;
import org.xcore.cloud.mindustry.selector.parser.PlayerSelectorAdapter;
import org.xcore.cloud.mindustry.selector.parser.SelectorSyntaxParser;
import org.xcore.cloud.mindustry.selector.parser.TargetSelectorParsers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SimulationExecutorTest {

    /** A stand-in for the game loop: one thread that runs what is queued for it. */
    private static final class Loop {
        final ExecutorService queue = Executors.newSingleThreadExecutor();
        final Thread thread;
        final SimulationExecutor simulation;

        Loop() throws Exception {
            thread = queue.submit(Thread::currentThread).get();
            simulation = SimulationExecutor.bound(thread, queue);
        }

        void run(Runnable task) throws Exception {
            queue.submit(task).get(10, TimeUnit.SECONDS);
        }
    }

    private static final class FakeApplication implements Application {
        final Seq<ApplicationListener> listeners = new Seq<>();
        final List<Runnable> posted = new ArrayList<>();
        Thread mainThread;

        @Override public Seq<ApplicationListener> getListeners() { return listeners; }
        @Override public ApplicationType getType() { return ApplicationType.headless; }
        @Override public String getClipboardText() { return null; }
        @Override public void setClipboardText(String text) {}
        @Override public void post(Runnable runnable) { posted.add(runnable); }
        @Override public void exit() {}
        @Override public Thread getMainThread() { return mainThread; }
    }

    private Loop loop;
    private List<String> messages;
    private List<Thread> threads;

    @BeforeAll
    static void initMindustry() {
        Vars.state = new GameState();
        Vars.headless = true;
        Vars.net = new mindustry.net.Net(null);
        if (Vars.content == null) {
            Vars.content = new ContentLoader();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        Groups.init();
        loop = new Loop();
        messages = new CopyOnWriteArrayList<>();
        threads = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() {
        loop.queue.shutdownNow();
    }

    private MindustryCommandManager<MindustrySender> manager(SimulationExecutor simulation, ExecutionCoordinator<MindustrySender> coordinator) {
        return new MindustryCommandManager<>(new CommandHandler(""), coordinator, SenderMapper.identity(), simulation);
    }

    private MindustrySender console() {
        return new MindustrySender() {
            @Override public void sendMessage(String message) { messages.add(message); threads.add(Thread.currentThread()); }
            @Override public String name() { return "Console"; }
            @Override public boolean isPlayer() { return false; }
            @Override public Player player() { return null; }
        };
    }

    private void addPlayer(String name) {
        Player p = Player.create();
        p.id = 1;
        p.name = name;
        p.team(Team.sharded);
        p.add();
    }

    @Test
    @DisplayName("A command issued from another thread is parsed, handled and answered on the simulation thread")
    void queuedCommand_runsOnOwner() throws Exception {
        var manager = manager(loop.simulation, ExecutionCoordinator.coordinatorFor(loop.simulation));
        loop.run(() -> addPlayer("alice"));
        manager.command(manager.commandBuilder("who")
                .required("target", TargetSelectorParsers.singlePlayerSelector(manager.selectorEngine()))
                .handler(ctx -> {
                    threads.add(Thread.currentThread());
                    SinglePlayerSelector target = ctx.get("target");
                    ctx.sender().sendMessage("found " + target.resolve(ctx.sender()).plainName());
                }));

        manager.commandExecutor().executeCommand(console(), "who alice").get(10, TimeUnit.SECONDS);
        assertThrows(Exception.class,
                () -> manager.commandExecutor().executeCommand(console(), "who nobody").get(10, TimeUnit.SECONDS));

        assertEquals("found alice", messages.get(0));
        assertEquals(2, messages.size(), messages.toString());
        assertEquals(4, threads.size()); // two handler runs, two messages
        assertTrue(threads.stream().allMatch(thread -> thread == loop.thread), threads.toString());
    }

    @Test
    @DisplayName("Resolving a selector from another thread fails instead of blocking")
    void workerResolve_fails() {
        var manager = manager(loop.simulation, ExecutionCoordinator.simpleCoordinator());

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> manager.selectorEngine().resolvePlayers(console(), SelectorSyntaxParser.parse("@a")));

        assertTrue(failure.getMessage().contains("simulation thread"), failure.getMessage());
    }

    @Test
    @DisplayName("A command pipeline left on another thread reports an error and does not run the handler")
    void workerPipeline_doesNotTouchGameState() throws Exception {
        var manager = manager(loop.simulation, ExecutionCoordinator.simpleCoordinator());
        loop.run(() -> addPlayer("alice"));
        List<String> executed = new ArrayList<>();
        manager.command(manager.commandBuilder("who")
                .required("target", PlayerSelectorAdapter.playerParser(manager, manager.selectorEngine()))
                .handler(ctx -> executed.add("who")));

        try {
            manager.commandExecutor().executeCommand(console(), "who alice").join();
        } catch (Exception ignored) {
            // Reported to the sender.
        }

        assertEquals(List.of(), executed);
        assertEquals(1, messages.size(), messages.toString());
    }

    @Test
    @DisplayName("Two managers keep their own simulation threads")
    void twoManagers() throws Exception {
        Loop other = new Loop();
        try {
            var first = manager(loop.simulation, ExecutionCoordinator.simpleCoordinator());
            var second = manager(other.simulation, ExecutionCoordinator.simpleCoordinator());
            TargetSelectorSpec everyone = SelectorSyntaxParser.parse("@a");
            List<Throwable> failures = new CopyOnWriteArrayList<>();

            loop.run(() -> {
                first.selectorEngine().resolvePlayers(console(), everyone);
                try {
                    second.selectorEngine().resolvePlayers(console(), everyone);
                } catch (IllegalStateException e) {
                    failures.add(e);
                }
            });
            other.run(() -> second.selectorEngine().resolvePlayers(console(), everyone));

            assertEquals(1, failures.size());
        } finally {
            other.queue.shutdownNow();
        }
    }

    @Test
    @DisplayName("forApplication runs inline on the main thread and posts from any other")
    void forApplication() {
        FakeApplication application = new FakeApplication();
        SimulationExecutor simulation = SimulationExecutor.forApplication(application);
        List<String> ran = new ArrayList<>();

        // The main thread is not known yet: no thread counts as the simulation thread.
        assertFalse(simulation.isOnThread());
        assertThrows(IllegalStateException.class, simulation::requireOnThread);
        simulation.execute(() -> ran.add("posted"));
        assertEquals(List.of(), ran);
        assertEquals(1, application.posted.size());

        application.mainThread = Thread.currentThread();
        assertTrue(simulation.isOnThread());
        simulation.execute(() -> ran.add("inline"));
        assertEquals(List.of("inline"), ran);
        assertEquals(1, application.posted.size());
    }

    @Test
    @DisplayName("After the application is disposed nothing more is queued for it")
    void forApplication_disposed() {
        FakeApplication application = new FakeApplication();
        application.mainThread = loop.thread;
        SimulationExecutor simulation = SimulationExecutor.forApplication(application);

        application.listeners.each(ApplicationListener::dispose);

        assertThrows(RejectedExecutionException.class, () -> simulation.execute(() -> {}));
        assertEquals(0, application.posted.size());
    }
}
