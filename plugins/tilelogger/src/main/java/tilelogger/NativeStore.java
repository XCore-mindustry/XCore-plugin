package tilelogger;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static java.lang.foreign.ValueLayout.*;

/** Typed downcalls to TileLoggerAbi.h; every returned byte buffer is copied into Java-owned values. */
final class NativeStore {
    static final long MAX_RESULT_BYTES = 8 * 1024 * 1024;
    private static final byte[] EMPTY = new byte[0];
    private static final MemoryLayout RESULT = MemoryLayout.structLayout(JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS);
    private final Linker linker = Linker.nativeLinker();
    private final SymbolLookup library;
    private final MethodHandle release, start, prepare, activate, discard, elapsed, record, history, plan,
            commit, forget, current, memory, readOnly, session, status, writable, build, flush;
    private final ThreadLocal<Call> calls = ThreadLocal.withInitial(Call::new);

    NativeStore(Path path) {
        if (ADDRESS.byteSize() != 8 || RESULT.byteSize() != 32)
            throw new IllegalStateException("TileLogger FFM requires a 64-bit JVM");
        // The native writer lives until JVM exit; unloading this library while it runs is unsafe.
        library = SymbolLookup.libraryLookup(path, Arena.global());
        var abi = linker.downcallHandle(library.find("tl_abi_version").orElseThrow(
                () -> new LinkageError("TileLogger library requires FFM ABI " + TileLogger.ABI)),
                FunctionDescriptor.of(JAVA_INT));
        try {
            if ((int) abi.invokeExact() != TileLogger.ABI)
                throw new LinkageError("TileLogger native ABI mismatch");
        } catch (Throwable failure) {
            throw propagate(failure);
        }
        release = linker.downcallHandle(library.findOrThrow("tl_release"), FunctionDescriptor.ofVoid(ADDRESS));
        start = bind("tl_start", ADDRESS, ADDRESS, ADDRESS);
        prepare = bind("tl_prepare", ADDRESS, ADDRESS);
        activate = bind("tl_activate", JAVA_LONG);
        discard = bind("tl_discard", JAVA_LONG);
        elapsed = bind("tl_elapsed");
        record = bind("tl_record", JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT, JAVA_INT,
                JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG, JAVA_INT);
        history = bind("tl_history", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG,
                JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG);
        plan = bind("tl_plan", JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_LONG,
                JAVA_INT, JAVA_LONG);
        commit = bind("tl_commit", JAVA_LONG, JAVA_INT, JAVA_INT);
        forget = bind("tl_forget", JAVA_LONG);
        current = bind("tl_current", JAVA_INT, JAVA_INT);
        memory = bind("tl_memory", JAVA_INT);
        readOnly = bind("tl_read_only");
        session = bind("tl_session");
        status = bind("tl_status");
        writable = bind("tl_writable");
        build = bind("tl_build");
        flush = bind("tl_flush");
    }

    private MethodHandle bind(String name, MemoryLayout... parameters) {
        var args = new MemoryLayout[parameters.length + 1];
        args[0] = ADDRESS;
        System.arraycopy(parameters, 0, args, 1, parameters.length);
        return linker.downcallHandle(library.findOrThrow(name), FunctionDescriptor.of(JAVA_INT, args));
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException error) return error;
        return new IllegalStateException("TileLogger FFM invocation failed", failure);
    }

    static MemorySegment allocateNative(Arena arena, long bytes, long alignment) {
        try {
            return arena.allocate(bytes, alignment);
        } catch (OutOfMemoryError failure) {
            // Preserve rollback recovery for native allocation failures, like the C ABI's status 3.
            throw new IllegalStateException("TileLogger native allocation failed", failure);
        }
    }

    private Call call() {
        var call = calls.get();
        if (call.inUse) throw new IllegalStateException("Reentrant TileLogger FFM call");
        call.inUse = true;
        return call;
    }

    private final class Call implements AutoCloseable {
        // Only this thread can obtain this private frame. The fixed 4 KiB block is GC-owned;
        // large arguments and borrowed results get a confined scope closed on every call.
        final Arena scratchArena = Arena.ofAuto();
        final MemorySegment scratch = allocateNative(scratchArena, 4096, 8);
        final MemorySegment out = scratch.asSlice(0, RESULT);
        Arena temporary;
        long cursor = RESULT.byteSize();
        boolean inUse;
        Arena temporary() {
            if (temporary == null) temporary = Arena.ofConfined();
            return temporary;
        }
        MemorySegment allocate(long bytes) {
            if (bytes > scratch.byteSize() - cursor) return allocateNative(temporary(), bytes, 1);
            var result = scratch.asSlice(cursor, bytes);
            cursor += bytes;
            return result;
        }
        MemorySegment string(String value) {
            if (value == null || value.indexOf('\0') >= 0)
                throw new IllegalArgumentException("Native strings must be non-null and contain no NUL");
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            var result = allocate(bytes.length + 1L);
            MemorySegment.copy(bytes, 0, result, JAVA_BYTE, 0, bytes.length);
            result.set(JAVA_BYTE, bytes.length, (byte) 0);
            return result;
        }
        MemorySegment bytes(byte[] value) {
            if (value.length == 0) return MemorySegment.NULL;
            var result = allocate(value.length);
            MemorySegment.copy(value, 0, result, JAVA_BYTE, 0, value.length);
            return result;
        }
        MemorySegment data() {
            long size = out.get(JAVA_LONG, 16);
            var address = out.get(ADDRESS, 8);
            if (size < 0 || size > MAX_RESULT_BYTES || (size > 0 && address.address() == 0))
                throw new IllegalStateException("Invalid native result bounds");
            return size == 0 ? MemorySegment.NULL : address.reinterpret(size, temporary(), null);
        }
        String text() {
            return new String(data().toArray(JAVA_BYTE), StandardCharsets.UTF_8);
        }
        void check(int code) {
            if (code == 0) return;
            // World mutation recovery catches RuntimeException, as it did for JNI std::bad_alloc.
            if (code == 3) throw new IllegalStateException("TileLogger native allocation failed");
            String message = text();
            if (message.isEmpty()) message = "TileLogger native call failed (" + code + ")";
            if (code == 1) throw new IllegalArgumentException(message);
            throw new IllegalStateException(message);
        }
        long value() {
            return out.get(JAVA_LONG, 0);
        }
        @Override
        public void close() {
            try {
                if (out.get(ADDRESS, 24).address() != 0)
                    release.invokeExact(out);
            } catch (Throwable failure) {
                throw propagate(failure);
            } finally {
                try {
                    if (temporary != null) temporary.close();
                } finally {
                    temporary = null;
                    out.fill((byte) 0);
                    cursor = RESULT.byteSize();
                    inUse = false;
                }
            }
        }
    }

    void start(String path, String id, String manifest) {
        try (var call = call()) {
            call.check((int) start.invokeExact(call.out, call.string(path), call.string(id), call.string(manifest)));
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    long prepare(String path, String manifest) {
        try (var call = call()) {
            call.check((int) prepare.invokeExact(call.out, call.string(path), call.string(manifest)));
            return call.value();
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    private void handle(MethodHandle operation, long id) {
        try (var call = call()) {
            call.check((int) operation.invokeExact(call.out, id));
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    void activate(long id) { handle(activate, id); }
    void discard(long id) { handle(discard, id); }
    void forget(long id) { handle(forget, id); }

    void record(int x, int y, String uuid, int team, int block, int rotation,
                ConfigCodec.Encoded config, ActionKind kind) {
        byte[] actor = uuid.isEmpty() ? EMPTY : uuid.getBytes(StandardCharsets.UTF_8);
        if (actor.length > 256) throw new IllegalArgumentException("Player UUID exceeds 256 UTF-8 bytes");
        try (var call = call()) {
            int size = config.type() < 5 ? 0 : config.byteSize();
            var payload = size == 0 ? MemorySegment.NULL : call.allocate(size);
            if (size > 0) config.copyBytesTo(payload);
            call.check((int) record.invokeExact(call.out, x, y, call.bytes(actor), (long) actor.length,
                    team, block, rotation, config.type(), config.scalar(), payload,
                    (long) size, kind.id));
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    TileState[] history(int x1, int y1, int x2, int y2, String uuid, int teams, long time, long size, long before) {
        byte[] actor = uuid.isEmpty() ? EMPTY : uuid.getBytes(StandardCharsets.UTF_8);
        if (actor.length > 256) throw new IllegalArgumentException("Player UUID exceeds 256 UTF-8 bytes");
        try (var call = call()) {
            call.check((int) history.invokeExact(call.out, x1, y1, x2, y2, call.bytes(actor),
                    (long) actor.length, teams, time, size, before));
            return decode(call.data());
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    TileState[] plan(Rect rect, String uuid, int teams, long time) {
        byte[] actor = uuid.isEmpty() ? EMPTY : uuid.getBytes(StandardCharsets.UTF_8);
        if (actor.length > 256) throw new IllegalArgumentException("Player UUID exceeds 256 UTF-8 bytes");
        try (var call = call()) {
            call.check((int) plan.invokeExact(call.out, rect.x1, rect.y1, rect.x2, rect.y2,
                    call.bytes(actor), (long) actor.length, teams, time));
            return decode(call.data());
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    void commit(TileState state) {
        try (var call = call()) {
            call.check((int) commit.invokeExact(call.out, state.planId, state.x, state.y));
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    TileState[] current(int x, int y) {
        try (var call = call()) {
            call.check((int) current.invokeExact(call.out, x, y));
            return decode(call.data());
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    long memory(int id) {
        try (var call = call()) {
            call.check((int) memory.invokeExact(call.out, id));
            return call.value();
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    private long number(MethodHandle operation) {
        try (var call = call()) {
            call.check((int) operation.invokeExact(call.out));
            return call.value();
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    private String text(MethodHandle operation) {
        try (var call = call()) {
            call.check((int) operation.invokeExact(call.out));
            return call.text();
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }
    long elapsed() { return number(elapsed); }
    boolean readOnly() { return number(readOnly) != 0; }
    String session() { return text(session); }
    String status() { return text(status); }
    String build() { return text(build); }
    void writable() { number(writable); }
    void flush() { number(flush); }

    /** The format is defined by rows() in TileLogger.cpp, independently of C++ struct layouts. */
    static TileState[] decode(MemorySegment memory) {
        if (memory.byteSize() < 4 || memory.byteSize() > MAX_RESULT_BYTES)
            throw new IllegalStateException("Invalid native rows size");
        var input = memory.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
        int count = input.getInt();
        if (count < 0 || count > 4097)
            throw new IllegalStateException("Invalid native row count");
        var states = new TileState[count];
        for (int i = 0; i < count; i++) {
            if (input.remaining() < 60) throw new IllegalStateException("Truncated native row");
            var state = new TileState();
            state.x = Short.toUnsignedInt(input.getShort());
            state.y = Short.toUnsignedInt(input.getShort());
            state.team = Short.toUnsignedInt(input.getShort());
            state.block = Short.toUnsignedInt(input.getShort());
            state.rotation = Byte.toUnsignedInt(input.get());
            state.config_type = Byte.toUnsignedInt(input.get());
            state.action_kind = Byte.toUnsignedInt(input.get());
            int flags = Byte.toUnsignedInt(input.get());
            state.valid = (flags & 1) != 0;
            state.destroy = (flags & 2) != 0;
            state.legacy = (flags & 4) != 0;
            state.configTruncated = (flags & 8) != 0;
            state.time = input.getLong();
            state.sequence = input.getLong();
            state.planId = input.getLong();
            state.expectedSequence = input.getLong();
            int scalar = input.getInt();
            state.configBytes = input.getInt();
            if ((flags & ~15) != 0 || state.rotation > 127 || state.config_type > 9
                    || (state.action_kind > ActionKind.ADMIN_FILL.id && state.action_kind != ActionKind.LEGACY.id)
                    || state.time < 0 || state.sequence < 0 || state.planId < 0 || state.expectedSequence < 0
                    || state.configBytes < 0 || state.configBytes > ConfigCodec.MAX_BYTES)
                throw new IllegalStateException("Invalid native row fields");
            state.uuid = new String(blob(input, 256), StandardCharsets.UTF_8);
            byte[] bytes = blob(input, ConfigCodec.MAX_BYTES);
            if (state.config_type < 5) {
                if (bytes.length != 0 || state.configBytes != 0 || state.configTruncated)
                    throw new IllegalStateException("Unexpected scalar payload");
                state.nativeConfig(scalar);
            } else {
                if (state.configTruncated ? bytes.length >= state.configBytes : bytes.length != state.configBytes)
                    throw new IllegalStateException("Inconsistent native configuration length");
                state.nativeConfig(bytes);
            }
            states[i] = state;
        }
        if (input.hasRemaining()) throw new IllegalStateException("Unexpected native rows data");
        return states;
    }
    private static byte[] blob(ByteBuffer input, int limit) {
        if (input.remaining() < 4) throw new IllegalStateException("Truncated native blob");
        int size = input.getInt();
        if (size < 0 || size > limit || size > input.remaining())
            throw new IllegalStateException("Invalid native blob length");
        if (size == 0) return EMPTY;
        byte[] bytes = new byte[size];
        input.get(bytes);
        return bytes;
    }
}
