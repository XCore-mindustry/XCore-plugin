package tilelogger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

class NativeStoreTest {
    @TempDir Path directory;
    private Path journal;
    @BeforeEach
    void start() {
        journal = directory.resolve("session.history");
        TileLogger.startSession(journal.toString(), UUID.randomUUID().toString(), "test-content");
    }
    @AfterEach
    void flush() {
        TileLogger.flush();
    }
    private void record(int x, int block, ActionKind kind) {
        TileLogger.record(x, 1, "actor", 9, block, 0, ConfigCodec.encode("конфигурация"), kind);
    }
    private long load(Path path) {
        return CompletableFuture.supplyAsync(() -> TileLogger.prepareArchive(path.toString(), "test-content"))
                .join();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 3, 4, 127})
    void preservesGameRotationThroughFfmAndArchive(int rotation) {
        TileLogger.record(1, 1, "actor", 9, 20, rotation, ConfigCodec.encode(null), ActionKind.CONFIGURE);
        assertThat(TileLogger.currentState(1, 1).rotation).isEqualTo(rotation);
        assertThat(TileLogger.getHistory(0, 0, -1, -1, "", -1, 0, 10)[0].rotation).isEqualTo(rotation);
        TileLogger.flush();
        TileLogger.activateArchive(load(journal));
        assertThat(TileLogger.currentState(1, 1).rotation).isEqualTo(rotation);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 128, 256})
    void rejectsRotationOutsideGameSaveRange(int rotation) {
        assertThatThrownBy(() -> TileLogger.record(1, 1, "private-actor", 9, 20, rotation,
                ConfigCodec.encode(null), ActionKind.CONFIGURE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid tile action:", "rotation=", "team=9", "block=20", "action=6")
                .hasMessageNotContaining("private-actor");
        assertThat(TileLogger.currentState(1, 1)).isNull();
    }
    @Test
    void invalidReadNeverClearsTheStore() {
        record
        (1, 2048, ActionKind.BUILD_COMPLETED);
        assertThatThrownBy(() -> TileLogger.activateArchive(-1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TileLogger.getHistory(0, 0, -1, -1, "", -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        TileState[] rows = TileLogger.getHistory(0, 0, -1, -1, "", -1, 0, 10);
        assertThat(rows).hasSize(1);
        assertThat(rows[0].team).isEqualTo(9);
        assertThat(rows[0].block).isEqualTo(2048);
        assertThat(rows[0].getConfig()).isEqualTo("конфигурация");
    }
    @Test
    void commitsOnlyTheSelectedPlanPositionsAndReloadsValidity() throws Exception {
        record
        (1, 20, ActionKind.BASELINE);
        record
        (2, 20, ActionKind.BASELINE);
        record
        (1, 21, ActionKind.BUILD_COMPLETED);
        record
        (2, 21, ActionKind.BUILD_COMPLETED);
        TileLogger.flush();
        byte[] before = Files.readAllBytes(journal);
        var plan = TileLogger.planRollback(new Rect(0, 0, 3, 3), "actor", -1, 0);
        assertThat(plan).hasSize(2);
        TileLogger.flush();
        assertThat(Files.readAllBytes(journal)).isEqualTo(before);
        TileLogger.commitRollback(plan[0]);
        TileLogger.discardPlan(plan[0].planId);
        assertThat(TileLogger.currentState(plan[0].x, 1).block).isEqualTo(20);
        assertThat(TileLogger.currentState(plan[1].x, 1).block).isEqualTo(21);
        TileLogger.flush();
        long loaded = load(journal);
        TileLogger.activateArchive(loaded);
        assertThat(TileLogger.isReadOnly()).isTrue();
        assertThat(TileLogger.currentState(plan[0].x, 1).block).isEqualTo(20);
        assertThat(TileLogger.currentState(plan[1].x, 1).block).isEqualTo(21);
        assertThatThrownBy(() -> record(3, 20, ActionKind.BUILD_COMPLETED))
                .isInstanceOf(IllegalStateException.class);
    }
    @Test
    void stalePlanCannotCommitAfterAnotherAction() {
        record
        (1, 20, ActionKind.BASELINE);
        record
        (1, 21, ActionKind.BUILD_COMPLETED);
        var plan = TileLogger.planRollback(new Rect(0, 0, 3, 3), "actor", -1, 0);
        record
        (2, 22, ActionKind.BUILD_COMPLETED);
        assertThatThrownBy(() -> TileLogger.commitRollback(plan[0]))
                .isInstanceOf(IllegalStateException.class);
        assertThat(TileLogger.currentState(1, 1).block).isEqualTo(21);
    }
    @Test
    void failedArchiveDoesNotReplaceLiveSession() throws Exception {
        record
        (1, 20, ActionKind.BUILD_COMPLETED);
        Path invalid = directory.resolve("broken.history");
        Files.writeString(invalid, "TL");
        assertThatThrownBy(() -> load(invalid)).hasCauseInstanceOf(IllegalStateException.class);
        assertThat(TileLogger.isReadOnly()).isFalse();
        assertThat(TileLogger.getHistory(0, 0, -1, -1, "", -1, 0, 10)).hasSize(1);
    }
    @Test
    void dictionaryAndFrameRoundtripAcrossLargeForeignResults() {
        for (int i = 0; i < 4097; i++)
            TileLogger.record(i, 1, "actor-" + i, 9, 20, 0, ConfigCodec.encode(new byte[] {1, 2, 3}),
                    ActionKind.CONFIGURE);
        var rows = TileLogger.getHistory(0, 0, -1, -1, "", -1, 0, 4097);
        assertThat(rows).hasSize(4097);
        assertThat(rows[4096].uuid).isEqualTo("actor-4096");
        assertThat((byte[]) rows[4096].getConfig()).containsExactly(1, 2, 3);
    }

    @Test
    void historyPreviewsBoundPayloadCopiesAndPlansRejectOversizedRestores() {
        byte[] bytes = new byte[ConfigCodec.MAX_BYTES];
        for (int x = 1; x <= 5; x++)
            TileLogger.record(x, 1, "actor", 1, 20, 0, ConfigCodec.encode(bytes), ActionKind.BUILD_COMPLETED);
        var preview = TileLogger.getHistory(0, 0, -1, -1, "", -1, 0, 10);
        assertThat(preview).hasSize(5);
        assertThat(preview[0].configTruncated).isTrue();
        assertThat(preview[0].configBytes).isEqualTo(ConfigCodec.MAX_BYTES);
        assertThat(preview[0].getConfigAsString()).isEqualTo(ConfigCodec.MAX_BYTES + "b");
        assertThatThrownBy(preview[0] ::getConfig).isInstanceOf(IllegalStateException.class);
        assertThat(((byte[]) TileLogger.currentState(1, 1).getConfig()).length)
                .isEqualTo(ConfigCodec.MAX_BYTES);
        assertThatThrownBy(() -> TileLogger.planRollback(new Rect(0, 0, 10, 10), "actor", -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsJournalProducedByThePreviousJniRelease() throws Exception {
        Path golden = directory.resolve("jni.history");
        try (var input = getClass().getResourceAsStream("/golden/jni-v2.history")) {
            assertThat(input).isNotNull();
            Files.copy(input, golden);
        }
        TileLogger.activateArchive(load(golden));
        assertThat(TileLogger.sessionId()).isEqualTo("jni-golden-session");
        assertThat(TileLogger.isReadOnly()).isTrue();
        var current = TileLogger.currentState(1, 2);
        assertThat(current.block).isEqualTo(2048);
        assertThat(current.rotation).isEqualTo(3);
        assertThat(current.getConfig()).isEqualTo(Integer.MIN_VALUE);
        var history = TileLogger.getHistory(1, 2, 1, 2, "", -1, 0, 10);
        assertThat(history).hasSize(2);
        assertThat(history[1].valid).isFalse();
        assertThat(history[1].getConfig()).isEqualTo("old 🧱\0config");
        assertThat((byte[]) TileLogger.currentState(2, 3).getConfig()).containsExactly(0, 1, (byte) 255);
        var preview = TileLogger.getHistory(4, 5, 4, 5, "", -1, 0, 1)[0];
        assertThat(preview.configTruncated).isTrue();
        assertThat(preview.configBytes).isEqualTo(4096);
    }

    @Test
    void returnedValuesSurviveReleaseAndSubsequentNativeCalls() {
        String actor = "игрок🧱\0";
        TileLogger.record(1, 1, actor, 9, 2048, 3, ConfigCodec.encode(new byte[] {1, 2, 3}),
                ActionKind.CONFIGURE);
        var row = TileLogger.currentState(1, 1);
        assertThatThrownBy(() -> TileLogger.getHistory(-1, 0, 2, 2, "", -1, 0, 10))
                .isInstanceOf(IllegalArgumentException.class);
        for (int i = 0; i < 100; i++) {
            TileLogger.getBuildString();
            TileLogger.currentState(1, 1);
        }
        assertThat(row.uuid).isEqualTo(actor);
        assertThat((byte[]) row.getConfig()).containsExactly(1, 2, 3);
        TileLogger.flush();
        TileLogger.activateArchive(load(journal));
        assertThat(TileLogger.currentState(1, 1).uuid).isEqualTo(actor);
    }

    @Test
    void utf8ArchivePathsAndSessionMetadataRoundtrip() {
        Path unicode = directory.resolve("історія-🧱.history");
        TileLogger.startSession(unicode.toString(), "сесія-🧱", "test-content");
        record(1, 2048, ActionKind.BUILD_COMPLETED);
        TileLogger.flush();
        TileLogger.checkWritable();
        assertThat(unicode).exists();
        record(2, 2049, ActionKind.BUILD_COMPLETED);
        TileLogger.flush();
        TileLogger.checkWritable();
        TileLogger.activateArchive(load(unicode));
        assertThat(TileLogger.sessionId()).isEqualTo("сесія-🧱");
        assertThat(TileLogger.currentState(1, 1).getConfig()).isEqualTo("конфигурация");
        assertThat(TileLogger.currentState(2, 1).block).isEqualTo(2049);
    }

    @Test
    void archiveFailuresKeepDiagnosticsLocalToEachForeignCall() {
        var tasks = new java.util.ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < 12; i++) {
            Path missing = directory.resolve("відсутній-🧱-" + i + ".history");
            tasks.add(CompletableFuture.runAsync(() -> assertThatThrownBy(
                    () -> TileLogger.prepareArchive(missing.toString(), "test-content"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining(missing.getFileName().toString())));
        }
        CompletableFuture.allOf(tasks.toArray(CompletableFuture[]::new)).join();
        assertThat(TileLogger.isReadOnly()).isFalse();
    }
}
