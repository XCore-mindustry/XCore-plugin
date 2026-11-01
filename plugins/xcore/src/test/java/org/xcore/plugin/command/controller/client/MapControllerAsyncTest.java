package org.xcore.plugin.command.controller.client;

import arc.files.Fi;
import arc.struct.StringMap;
import mindustry.Vars;
import mindustry.game.Rules;
import mindustry.gen.Player;
import mindustry.maps.Map;
import mindustry.net.NetConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.xcore.plugin.cloud.XCoreSender;
import org.xcore.plugin.concurrent.Async;
import org.xcore.plugin.concurrent.StorageExecutor;
import org.xcore.plugin.database.repository.MapDataRepository;
import org.xcore.plugin.model.MapData;
import org.xcore.plugin.service.MapService;
import org.xcore.plugin.ui.menu.MapMenu;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Commands are dispatched from the game thread, so the map statistics lookup is the one
 * place in this controller that must not reach the repository inline.
 */
class MapControllerAsyncTest {

    private mindustry.core.GameState originalState;

    @BeforeEach
    void installGameState() {
        // The controller reads Vars.state.rules.mode() while it still owns the game thread,
        // so a state has to exist for the capture to be meaningful.
        originalState = Vars.state;
        Vars.state = new mindustry.core.GameState();
        clearStorage();
    }

    @AfterEach
    void restoreGameState() {
        Vars.state = originalState;
    }

    @Test
    @DisplayName("/map runs the repository lookup off the game thread and the menu on it")
    void map_defersRepositoryWorkAndMenuOpen() {
        Deque<Runnable> pending = new ArrayDeque<>();
        MapDataRepository repository = mock(MapDataRepository.class);
        MapMenu menu = mock(MapMenu.class);
        MapController controller = controller(repository, menu, pending);

        MapData data = MapData.builder().name("Crossroads").build();
        when(repository.findOrCreate(anyString(), anyString(), anyString(), anyString())).thenReturn(data);
        XCoreSender sender = senderOf(onlinePlayer());
        when(menu.getUuid(sender)).thenReturn("flow-id");

        controller.map(sender, mockMap());

        // Step 1: still on the game thread, nothing blocking has happened yet.
        verify(repository, never()).findOrCreate(anyString(), anyString(), anyString(), anyString());
        verify(menu, never()).map(anyString(), any(MapData.class));
        assertThat(pending).as("no continuation before the storage phase").isEmpty();

        // Step 2: the storage phase runs the lookup.
        runStorage();
        verify(repository).findOrCreate(
                eq("Crossroads"), eq("crossroads.msav"), eq("somebody"), eq("survival"));
        verify(menu, never()).map(anyString(), any(MapData.class));
        assertThat(pending).as("the result is queued, not applied").hasSize(1);

        // Step 3: the game thread applies it.
        pending.remove().run();
        verify(menu).map(eq("flow-id"), eq(data));
    }

    @Test
    @DisplayName("/map drops the menu when the player left during the lookup")
    void map_skipsMenuWhenPlayerDisconnected() {
        Deque<Runnable> pending = new ArrayDeque<>();
        MapDataRepository repository = mock(MapDataRepository.class);
        MapMenu menu = mock(MapMenu.class);
        MapController controller = controller(repository, menu, pending);

        when(repository.findOrCreate(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(MapData.builder().name("Crossroads").build());

        Player player = onlinePlayer();
        controller.map(senderOf(player), mockMap());

        disconnect(player);
        runStorage();
        pending.remove().run();

        // The row was still read - the map is real regardless of who asked for it - but
        // there is nobody left to show the menu to.
        verify(repository).findOrCreate(anyString(), anyString(), anyString(), anyString());
        verify(menu, never()).map(anyString(), any(MapData.class));
    }

    @Test
    @DisplayName("/map from the console is refused instead of throwing on a null player")
    void map_consoleSenderIsRefused() {
        Deque<Runnable> pending = new ArrayDeque<>();
        MapDataRepository repository = mock(MapDataRepository.class);
        MapController controller = controller(repository, mock(MapMenu.class), pending);

        // The console has no player, and forPlayer requires one. Before the guard this was
        // an NPE thrown into the command dispatcher.
        XCoreSender console = mock(XCoreSender.class);
        when(console.isPlayer()).thenReturn(false);
        when(console.player()).thenReturn(null);

        controller.map(console, mockMap());

        assertThat(pending).isEmpty();
        assertThat(STORAGE).isEmpty();
        verify(repository, never()).findOrCreate(anyString(), anyString(), anyString(), anyString());
        verify(console).send(eq("error-only-players"), any());
    }

    @Test
    @DisplayName("/map rejects a missing map without scheduling anything")
    void map_missingMap_schedulesNothing() {
        Deque<Runnable> pending = new ArrayDeque<>();
        MapController controller = controller(mock(MapDataRepository.class), mock(MapMenu.class), pending);
        XCoreSender sender = senderOf(onlinePlayer());

        controller.map(sender, null);

        assertThat(pending).isEmpty();
        assertThat(STORAGE).isEmpty();
        verify(sender).send(eq("error-map-not-found"), any());
    }

    /**
     * A real Map, not a mock: {@code file} is a final field, so Mockito cannot populate it
     * and the controller would dereference null while reading the file name.
     */
    private static Map mockMap() {
        StringMap tags = new StringMap();
        tags.put("name", "Crossroads");
        tags.put("author", "somebody");
        tags.put("description", "a test map");

        Fi file = mock(Fi.class);
        when(file.name()).thenReturn("crossroads.msav");
        return new Map(file, 10, 10, tags, true);
    }

    private static XCoreSender senderOf(Player player) {
        XCoreSender sender = mock(XCoreSender.class);
        // Mockito defaults booleans to false, and the controller now rejects non-player
        // senders before doing any work, so this has to be stated explicitly.
        when(sender.isPlayer()).thenReturn(true);
        when(sender.player()).thenReturn(player);
        return sender;
    }

    private static Player onlinePlayer() {
        Player player = mock(Player.class);
        when(player.isAdded()).thenReturn(true);
        NetConnection connection = mock(NetConnection.class);
        when(connection.isConnected()).thenReturn(true);
        player.con = connection;
        return player;
    }

    private static void disconnect(Player player) {
        when(player.isAdded()).thenReturn(false);
    }

    private static MapController controller(MapDataRepository repository, MapMenu menu, Deque<Runnable> pending) {
        return new MapController(repository, mock(MapService.class), menu,
                new Async(manualStorage(), pending::add));
    }

    private static final List<Runnable> STORAGE = new CopyOnWriteArrayList<>();

    /**
     * A storage executor that queues rather than runs. The inline executor used elsewhere
     * completes the future synchronously, which suits those tests but would make this one
     * racy: it could not tell a deferred lookup from one that already happened.
     */
    private static StorageExecutor manualStorage() {
        StorageExecutor executor = mock(StorageExecutor.class);
        when(executor.supply(org.mockito.ArgumentMatchers.<Callable<Object>>any()))
                .thenAnswer(invocation -> {
                    Callable<Object> task = invocation.getArgument(0);
                    CompletableFuture<Object> future = new CompletableFuture<>();
                    STORAGE.add(() -> {
                        try {
                            future.complete(task.call());
                        } catch (Throwable ex) {
                            future.completeExceptionally(ex);
                        }
                    });
                    return future;
                });
        return executor;
    }

    /** Run the queued storage work, completing the future the controller waits on. */
    private static void runStorage() {
        STORAGE.remove(0).run();
    }

    private static void clearStorage() {
        STORAGE.clear();
    }
}
