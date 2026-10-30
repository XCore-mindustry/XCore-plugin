#include <atomic>
#include <cstddef>
#include <cstring>
#include <memory>
#include <mutex>
#include "TileLoggerAbi.h"
#include "TileLogger.h"

static_assert(sizeof(void*) == 8 && sizeof(TLResult) == 32);
static_assert(offsetof(TLResult, data) == 8 && offsetof(TLResult, size) == 16 &&
              offsetof(TLResult, owner) == 24);
static JournalWriter writer;
static auto active = std::make_unique<TileLogger>(writer);
static std::mutex prepared_mutex;
static std::unordered_map<uint64_t, std::unique_ptr<TileLogger>> prepared;
static std::atomic<uint64_t> next_handle{};
static constexpr size_t max_result_bytes = 8 * 1024 * 1024;

static void publish(TLResult* result, DataVec bytes) {
    if (bytes.empty()) return;
    if (bytes.size() > max_result_bytes) throw std::runtime_error("Native result exceeds 8 MiB");
    auto owned = std::make_unique<DataVec>(std::move(bytes));
    result->data = owned->data();
    result->size = owned->size();
    result->owner = owned.release();
}
static void text(TLResult* result, const std::string& value) {
    publish(result, DataVec(value.begin(), value.end()));
}
static int32_t failure(TLResult* result, int32_t code, const char* message) noexcept {
    try {
        publish(result, DataVec(message, message + std::min(std::strlen(message), size_t{1024})));
        return code;
    } catch (...) {
        return 3;
    }
}
// No exception or borrowed C++ object can cross the C ABI, including a failure to allocate diagnostics.
template <class F> static int32_t checked(TLResult* result, F action) noexcept {
    if (!result) return 1;
    *result = {};
    try {
        action();
        return 0;
    } catch (const std::bad_alloc&) {
        return 3;
    } catch (const std::invalid_argument& error) {
        return failure(result, 1, error.what());
    } catch (const std::exception& error) {
        return failure(result, 2, error.what());
    } catch (...) {
        return failure(result, 2, "Unknown native failure");
    }
}
static DataVec bytes(const uint8_t* data, uint64_t size, size_t limit) {
    if (size > limit || (size && !data)) throw std::invalid_argument("Invalid native byte slice");
    return size ? DataVec(data, data + size) : DataVec{};
}
static std::string string(const char* value) {
    if (!value) throw std::invalid_argument("Null string");
    return value;
}
static Pos position(int32_t x, int32_t y) {
    if (x < 0 || x > 65535 || y < 0 || y > 65535) throw std::invalid_argument("Invalid tile coordinates");
    return Pos(x, y);
}
static Rect rectangle(int32_t x1, int32_t y1, int32_t x2, int32_t y2) {
    if (x2 == -1) x2 = 65535;
    if (y2 == -1) y2 = 65535;
    auto p1 = position(x1, y1), p2 = position(x2, y2);
    if (p1.x > p2.x || p1.y > p2.y) throw std::invalid_argument("Invalid rectangle");
    return Rect(p1.x, p1.y, p2.x, p2.y);
}
static std::filesystem::path archivePath(const char* value) {
    auto input = string(value);
    if (input.empty()) return {};
    auto path = std::filesystem::u8path(input);
    if (path.is_relative()) {
        path = path.lexically_normal();
        if (*path.begin() == "..") throw std::invalid_argument("Archive path escapes tilelogs");
        path = std::filesystem::path("config/tilelogs") / path;
    }
    return path.replace_extension("history");
}

// Versioned by tl_abi_version: little-endian count, then fixed-width fields and length-prefixed blobs.
// No C++ object layout is exposed. Preview limits match the former JNI API.
static void rows(TLResult* result, const std::vector<TileState>& states, bool preview = false) {
    Encoder out;
    out.integer(states.size(), 4);
    for (const auto& s : states) {
        const DataVec* config = s.config_type >= 5 ? &active->GetConfig(s.config) : nullptr;
        size_t full = config ? config->size() : 0;
        size_t length = preview ? std::min(full, s.config_type == 7 ? size_t{64} : size_t{1028}) : full;
        unsigned flags = (s.valid ? 1 : 0) | (s.destroy ? 2 : 0) |
                         (active->Legacy() ? 4 : 0) | (length < full ? 8 : 0);
        out.integer(s.pos.x, 2);
        out.integer(s.pos.y, 2);
        out.integer(s.team, 2);
        out.integer(s.block, 2);
        out.integer(s.rotation, 1);
        out.integer(s.config_type, 1);
        out.integer(static_cast<uint8_t>(s.action), 1);
        out.integer(flags, 1);
        out.integer(s.time, 8);
        out.integer(s.sequence, 8);
        out.integer(s.plan, 8);
        out.integer(s.expected, 8);
        out.integer(s.config, 4);
        out.integer(full, 4);
        out.blob(active->GetPlayer(s.player));
        out.integer(length, 4);
        if (length) out.bytes.insert(out.bytes.end(), config->begin(), config->begin() + length);
        if (out.bytes.size() > max_result_bytes) throw std::runtime_error("Native result exceeds 8 MiB");
    }
    publish(result, std::move(out.bytes));
}

int32_t tl_abi_version() noexcept { return 3; }
void tl_release(TLResult* result) noexcept {
    if (!result) return;
    delete static_cast<DataVec*>(result->owner);
    *result = {};
}
int32_t tl_start(TLResult* result, const char* path, const char* session, const char* manifest) noexcept {
    return checked(result, [&] {
        auto candidate = std::make_unique<TileLogger>(writer);
        candidate->Start(archivePath(path), string(session), string(manifest));
        active = std::move(candidate);
    });
}
int32_t tl_prepare(TLResult* result, const char* path, const char* manifest) noexcept {
    return checked(result, [&] {
        auto resolved = archivePath(path);
        if (resolved.empty()) throw std::invalid_argument("Archive path required");
        auto expected = string(manifest);
        writer.Flush();
        auto candidate = std::make_unique<TileLogger>(writer);
        candidate->Load(resolved);
        if (!candidate->Legacy() && candidate->Manifest() != expected)
            throw std::invalid_argument("Archive content manifest differs from the current server");
        std::lock_guard lock(prepared_mutex);
        if (prepared.size() >= 4) throw std::runtime_error("Too many pending archive readers");
        auto handle = ++next_handle;
        prepared.emplace(handle, std::move(candidate));
        result->value = handle;
    });
}
int32_t tl_activate(TLResult* result, uint64_t handle) noexcept {
    return checked(result, [&] {
        std::lock_guard lock(prepared_mutex);
        auto found = prepared.find(handle);
        if (found == prepared.end()) throw std::invalid_argument("Unknown archive handle");
        active = std::move(found->second);
        prepared.erase(found);
    });
}
int32_t tl_discard(TLResult* result, uint64_t handle) noexcept {
    return checked(result, [&] {
        std::lock_guard lock(prepared_mutex);
        prepared.erase(handle);
    });
}
int32_t tl_elapsed(TLResult* result) noexcept {
    return checked(result, [&] { result->value = active->Duration(); });
}
int32_t tl_record(TLResult* result, int32_t x, int32_t y, const uint8_t* uuid, uint64_t uuid_size,
                  int32_t team, int32_t block, int32_t rotation, int32_t type, int32_t scalar,
                  const uint8_t* payload, uint64_t payload_size, int32_t kind) noexcept {
    return checked(result, [&] {
        if (kind < 0 || kind > 255) throw std::invalid_argument("Invalid action kind");
        active->Record(position(x, y), bytes(uuid, uuid_size, 256), team, block, rotation, type,
                       static_cast<uint32_t>(scalar), bytes(payload, payload_size, max_config_bytes),
                       static_cast<ActionKind>(kind));
    });
}
int32_t tl_history(TLResult* result, int32_t x1, int32_t y1, int32_t x2, int32_t y2,
                   const uint8_t* uuid, uint64_t uuid_size, int32_t teams, int64_t time,
                   int64_t size, int64_t before) noexcept {
    return checked(result, [&] {
        if (size < 1 || size > 4097 || before < 1) throw std::invalid_argument("Invalid history limit/cursor");
        rows(result, active->GetHistory(rectangle(x1, y1, x2, y2), bytes(uuid, uuid_size, 256),
                                         teams, time, static_cast<size_t>(size), before), true);
    });
}
int32_t tl_plan(TLResult* result, int32_t x1, int32_t y1, int32_t x2, int32_t y2,
                const uint8_t* uuid, uint64_t uuid_size, int32_t teams, int64_t time) noexcept {
    return checked(result, [&] {
        rows(result, active->PlanRollback(rectangle(x1, y1, x2, y2), bytes(uuid, uuid_size, 256), teams, time));
    });
}
int32_t tl_commit(TLResult* result, uint64_t plan, int32_t x, int32_t y) noexcept {
    return checked(result, [&] { active->Commit(plan, position(x, y)); });
}
int32_t tl_forget(TLResult* result, uint64_t plan) noexcept {
    return checked(result, [&] { active->Discard(plan); });
}
int32_t tl_current(TLResult* result, int32_t x, int32_t y) noexcept {
    return checked(result, [&] {
        auto value = active->Current(position(x, y));
        rows(result, value ? std::vector<TileState>{*value} : std::vector<TileState>{});
    });
}
int32_t tl_memory(TLResult* result, int32_t id) noexcept {
    return checked(result, [&] { result->value = active->MemoryUsage(id); });
}
int32_t tl_read_only(TLResult* result) noexcept {
    return checked(result, [&] { result->value = active->ReadOnly(); });
}
int32_t tl_session(TLResult* result) noexcept {
    return checked(result, [&] { text(result, active->Session()); });
}
int32_t tl_status(TLResult* result) noexcept {
    return checked(result, [&] { text(result, active->Status()); });
}
int32_t tl_writable(TLResult* result) noexcept {
    return checked(result, [&] { active->CheckWritable(); });
}
int32_t tl_build(TLResult* result) noexcept {
    return checked(result, [&] { text(result, "FFM ABI 3, journal v2 " __DATE__ " " __TIME__); });
}
int32_t tl_flush(TLResult* result) noexcept {
    return checked(result, [&] { writer.Flush(); });
}
