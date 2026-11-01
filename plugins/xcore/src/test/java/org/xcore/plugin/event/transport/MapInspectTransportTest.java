package org.xcore.plugin.event.transport;

import arc.func.Cons;
import com.sun.net.httpserver.HttpServer;
import mindustry.Vars;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.xcore.plugin.concurrent.*;
import org.xcore.plugin.config.TomlXcoreConfig;
import org.xcore.plugin.config.TomlSecretsConfig;
import org.xcore.plugin.database.repository.*;
import org.xcore.plugin.service.*;
import org.xcore.plugin.service.map.MapInspectionService;
import org.xcore.plugin.service.map.inspect.*;
import org.xcore.protocol.generated.messages.maps.MapsMessages.*;
import org.xcore.protocol.generated.shared.MapFileSourceV1;
import org.xcore.testkit.fixtures.HeadlessWorld;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MapInspectTransportTest {
    @TempDir Path directory;
    private final NetworkService network = mock(NetworkService.class);
    private final MapInspectionService service = mock(MapInspectionService.class);
    private final TomlXcoreConfig config = new TomlXcoreConfig();
    private final BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> replies = new LinkedBlockingQueue<>();
    private final AtomicReference<Cons<MapsInspectRequestV1>> listener = new AtomicReference<>();
    private StorageExecutor storage;
    private MapTransportHandler handler;
    private final MapInspectionService.Context context = new MapInspectionService.Context(MsavFixture.registry(), MsavFixture.settings());

    @BeforeEach
    void start() {
        GameThread.setGameThreadOverride(Thread.currentThread());
        config.server.name = "survival";
        storage = new StorageExecutor(8);
        handler = new MapTransportHandler(network, config, mock(MapService.class), mock(MapDataRepository.class),
                new Async(storage, main::add), mock(MapUploadRepository.class), service);
        doAnswer(invocation -> { listener.set(invocation.getArgument(1)); return null; }).when(network).subscribe(eq(MapsInspectRequestV1.class), any());
        doAnswer(invocation -> {
            assertThat(GameThread.isGameThread()).isFalse(); replies.add(invocation.getArgument(1)); return null;
        }).when(network).respond(any(), any());
        doAnswer(invocation -> {
            assertThat(GameThread.isGameThread()).isFalse(); replies.add(List.of(invocation.getArgument(1), invocation.getArgument(2))); return null;
        }).when(network).respondError(any(), anyString(), anyString());
        when(service.capture()).thenAnswer(ignored -> { assertThat(GameThread.isGameThread()).isTrue(); return context; });
        handler.registerListeners();
    }

    @AfterEach
    void stop() { storage.shutdown(); GameThread.setGameThreadOverride(null); }

    @Test
    void otherServerNeverCapturesDownloadsOrResponds() throws Exception {
        redis(() -> listener.get().get(request("other", null)));
        verifyNoInteractions(service); assertThat(main).isEmpty(); assertThat(replies).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(MapInspectionResult.Status.class)
    void typedResponsesPublishOnWorkerAndUseGeneratedNestedPayloads(MapInspectionResult.Status status) throws Exception {
        var snapshot = new DetachedMsavReader().read(new MsavFixture().write(directory), "a".repeat(64), MsavFixture.registry(), MsavFixture.settings());
        var result = status == MapInspectionResult.Status.OK
                ? new MapInspectionResult(status, null, snapshot, "tower-defense", List.of(new MapFinding("td-waves-enabled", MapFinding.Severity.INFO, "Saved waves", 1, 2, 3)))
                : MapInspectionResult.failed(status, "Failure detail");
        when(service.inspect(any(), any(), any())).thenAnswer(invocation -> {
            assertThat(GameThread.isGameThread()).isFalse(); assertThat(invocation.getArgument(2, MapInspectionService.Context.class)).isSameAs(context); return result;
        });
        var request = request("survival", "tower-defense");
        redis(() -> listener.get().get(request));
        verify(service, never()).capture();
        runMain();
        var response = (MapsInspectResponseV1) reply();
        assertThat(response.server()).isEqualTo("survival");
        assertThat(response.status().toString()).isEqualTo(status.name().toLowerCase(java.util.Locale.ROOT));
        verify(service).inspect(request.file(), request.profile(), context);
        Map<String, Object> payload = response.toPayload();
        assertThat(payload).containsEntry("messageType", "maps.inspect.response").containsEntry("messageVersion", 1);
        if (status == MapInspectionResult.Status.OK) {
            assertThat(response.info().profile()).isEqualTo("tower-defense");
            assertThat(response.info().gameMode()).isEqualTo("survival");
            assertThat(response.info().coresByTeam()).containsEntry("sharded", 1);
            assertThat(payload.get("info")).isInstanceOf(Map.class);
            assertThat((List<?>) payload.get("findings")).singleElement().isInstanceOf(Map.class);
            assertThat(response.findings().getFirst().toPayload()).containsEntry("severity", "info").containsEntry("x", 1).containsEntry("count", 3);
        } else { assertThat(response.info()).isNull(); assertThat(response.findings()).isEmpty(); }
    }

    @Test
    void excessiveTilesOmitUnobservedTileMetadataAndRetainTheFinding() throws Exception {
        var settings = new InspectionSettings(4194304, 24, Map.of());
        var snapshot = new DetachedMsavReader().read(new MsavFixture().write(directory), "b".repeat(64), MsavFixture.registry(), settings);
        var result = new MapInspectionResult(MapInspectionResult.Status.OK, "Tile checks skipped", snapshot, "survival", new MapChecks().check(snapshot, "survival", settings));
        when(service.inspect(any(), any(), any())).thenReturn(result);
        redis(() -> listener.get().get(request("survival", null))); runMain();
        var response = (MapsInspectResponseV1) reply();
        assertThat(response.info().toPayload()).doesNotContainKeys("coresByTeam", "spawnCount");
        assertThat(response.findings()).singleElement().satisfies(f -> assertThat(f.code()).isEqualTo("size-out-of-range"));
    }

    @Test
    void captureAndUnexpectedInspectionFailuresUseExistingRpcErrorCodes() throws Exception {
        when(service.capture()).thenThrow(new IllegalStateException("Content unavailable"));
        redis(() -> listener.get().get(request("survival", null))); runMain();
        assertThat(reply()).isEqualTo(List.of("FAILED", "Map inspection registry could not be captured"));
        doReturn(context).when(service).capture();
        when(service.inspect(any(), any(), any())).thenThrow(new IllegalStateException("Unexpected failure"));
        redis(() -> listener.get().get(request("survival", null))); runMain();
        assertThat(reply()).isEqualTo(List.of("FAILED", "Map inspection failed"));
    }

    @Test
    void concurrentRequestsAreBoundedAndBusyErrorStaysOffTheGameThread() throws Exception {
        var inside = new CountDownLatch(4); var release = new CountDownLatch(1);
        when(service.inspect(any(), any(), any())).thenAnswer(ignored -> {
            inside.countDown(); if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Worker not released");
            return MapInspectionResult.failed(MapInspectionResult.Status.INVALID_MAP, "Fixture");
        });
        try {
            for (int i = 0; i < 4; i++) redis(() -> listener.get().get(request("survival", null)));
            for (int i = 0; i < 4; i++) runMain();
            assertThat(inside.await(2, TimeUnit.SECONDS)).isTrue();
            redis(() -> listener.get().get(request("survival", null)));
            assertThat(reply()).isEqualTo(List.of("REJECTED", "Map inspection is busy"));
        } finally { release.countDown(); }
        for (int i = 0; i < 4; i++) assertThat(reply()).isInstanceOf(MapsInspectResponseV1.class);
    }

    @Test
    void storageRejectionAnswersPeerWithoutSchedulingCapture() throws Exception {
        storage.shutdown();
        redis(() -> listener.get().get(request("survival", null)));
        assertThat(reply()).isEqualTo(List.of("REJECTED", "Map inspection storage is busy"));
        assertThat(main).isEmpty(); verify(service, never()).capture();
    }

    @Test
    void actualInspectionRpcDownloadsDecodesPublishesAndCleansUpWithoutChangingTheRunningGame() throws Exception {
        byte[] bytes = new MsavFixture().compressed();
        var http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        http.createContext("/map", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        http.start();
        try (var engine = HeadlessWorld.create(8, 8);
             var downloader = new MapDownloader(Duration.ofSeconds(3), directory)) {
            Vars.state.wave = 57;
            var world = Vars.world;
            var state = Vars.state;
            var content = Vars.content;
            var rules = state.rules;
            var actualService = new MapInspectionService(new TomlSecretsConfig(), downloader, new DetachedMapReadLock());
            var actualHandler = new MapTransportHandler(network, config, mock(MapService.class), mock(MapDataRepository.class),
                    new Async(storage, main::add), mock(MapUploadRepository.class), actualService);
            actualHandler.registerListeners();
            var file = new MapFileSourceV1("http://127.0.0.1:" + http.getAddress().getPort() + "/map", "fixture.msav", null);
            redis(() -> listener.get().get(new MapsInspectRequestV1("survival", file, null)));
            runMain();
            var response = (MapsInspectResponseV1) reply();
            assertThat(response.status().toString()).isEqualTo("ok");
            assertThat(response.info().name()).isEqualTo("Fixture");
            assertThat(response.info().profile()).isEqualTo("survival");
            assertThat(response.info().width()).isEqualTo(5);
            assertThat(response.info().height()).isEqualTo(5);
            assertThat(response.info().coresByTeam()).containsEntry("sharded", 1);
            assertThat(response.info().spawnCount()).isEqualTo(1);
            assertThat(response.info().sizeBytes()).isEqualTo(bytes.length);
            assertThat(response.info().sha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            assertThat(response.findings()).noneMatch(finding -> finding.code().equals("no-core") || finding.code().equals("no-spawn"));
            assertThat(Vars.world).isSameAs(world);
            assertThat(Vars.state).isSameAs(state);
            assertThat(Vars.content).isSameAs(content);
            assertThat(state.rules).isSameAs(rules);
            assertThat(state.wave).isEqualTo(57);
            try (var files = Files.list(directory)) { assertThat(files).isEmpty(); }
        } finally {
            http.stop(0);
        }
    }

    private MapsInspectRequestV1 request(String server, String profile) {
        return new MapsInspectRequestV1(server, new MapFileSourceV1("https://example.invalid/fixture", "fixture.msav", null), profile);
    }

    private void runMain() throws Exception {
        Runnable work = main.poll(2, TimeUnit.SECONDS); assertThat(work).isNotNull(); work.run();
    }
    private Object reply() throws Exception { Object reply = replies.poll(3, TimeUnit.SECONDS); assertThat(reply).isNotNull(); return reply; }
    private static void redis(Runnable task) throws Exception {
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) { worker.submit(task).get(2, TimeUnit.SECONDS); }
    }
}
