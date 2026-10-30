// Release-only benchmark harness for the native core. Method and A/B usage: README "Benchmarks".
#if !defined(__OPTIMIZE__) || !defined(NDEBUG) || defined(__SANITIZE_ADDRESS__)
#error "Build benchmarks with optimization and -DNDEBUG, without sanitizers"
#endif
#if defined(__has_feature)
#if __has_feature(address_sanitizer)
#error "Build benchmarks without sanitizers"
#endif
#endif
#ifndef __linux__
#error "The harness reads /proc/self/status, available on Linux only"
#endif

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <new>
#include <optional>
#include <set>
#include <string>
#include <thread>
#include <unordered_set>
#include <vector>
#include <sys/vfs.h>
#include "TileLogger.h"

// Bump when fixtures, queries or output change; results from different versions are not comparable.
constexpr int kHarnessVersion = 1;

// Counts every requested C++ heap byte, including containers that MemoryUsage() does not report. The size
// lives in a header before the block: usable sizes vary with heap history, requested sizes do not.
static std::atomic<size_t> live_bytes{}, peak_bytes{};
constexpr size_t kHeader = 16;  // keeps malloc's 16-byte alignment
static void* counted(void* block, size_t offset, size_t size) {
    if (!block) throw std::bad_alloc();
    auto p = static_cast<char*>(block) + offset;
    std::memcpy(p - kHeader, &size, sizeof size);
    size_t now = live_bytes.fetch_add(size, std::memory_order_relaxed) + size;
    size_t peak = peak_bytes.load(std::memory_order_relaxed);
    while (now > peak && !peak_bytes.compare_exchange_weak(peak, now, std::memory_order_relaxed)) {
    }
    return p;
}
// Not inlined: GCC otherwise flags the header access as out of bounds of the object new returned.
[[gnu::noinline]] static void release(void* p, size_t offset) noexcept {
    if (!p) return;
    size_t size;
    std::memcpy(&size, static_cast<char*>(p) - kHeader, sizeof size);
    live_bytes.fetch_sub(size, std::memory_order_relaxed);
    std::free(static_cast<char*>(p) - offset);
}
static size_t alignedOffset(std::align_val_t align) { return std::max(kHeader, static_cast<size_t>(align)); }
void* operator new(std::size_t size) { return counted(std::malloc(size + kHeader), kHeader, size); }
void* operator new(std::size_t size, std::align_val_t align) {
    size_t offset = alignedOffset(align);
    return counted(std::aligned_alloc(offset, (size + 2 * offset - 1) / offset * offset), offset, size);
}
void operator delete(void* p) noexcept { release(p, kHeader); }
void operator delete(void* p, std::size_t) noexcept { release(p, kHeader); }
void operator delete(void* p, std::align_val_t align) noexcept { release(p, alignedOffset(align)); }
void operator delete(void* p, std::size_t, std::align_val_t align) noexcept { release(p, alignedOffset(align)); }

// Call stop() right after the measured work, so digests and formatting are not counted.
struct Memory {
    size_t base = live_bytes.load();
    int64_t live{}, peak{};
    Memory() { peak_bytes.store(base); }
    void stop() {
        live = static_cast<int64_t>(live_bytes.load()) - static_cast<int64_t>(base);
        peak = static_cast<int64_t>(peak_bytes.load()) - static_cast<int64_t>(base);
    }
};

using Clock = std::chrono::steady_clock;
static int64_t since(Clock::time_point start) {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - start).count();
}

// FNV-1a over results: keeps them observable to the optimizer and must match between base and head.
struct Digest {
    uint64_t value = 1469598103934665603ull;
    void add(uint64_t v) {
        for (int i = 0; i < 8; ++i) {
            value ^= (v >> (i * 8)) & 0xff;
            value *= 1099511628211ull;
        }
    }
    void add(const std::string& text) {
        for (char c : text) add(static_cast<uint8_t>(c));
    }
    // Logger records carry wall-clock time, so their digests leave it out.
    void add(const TileState& s, bool time = true) {
        for (uint64_t v : {uint64_t{s.pos.x}, uint64_t{s.pos.y}, uint64_t{s.player}, uint64_t{s.team},
                           uint64_t{s.valid}, time ? s.time : 0, uint64_t{s.block}, uint64_t{s.destroy},
                           uint64_t{s.rotation}, uint64_t{s.config_type}, uint64_t{s.config},
                           uint64_t{static_cast<uint8_t>(s.action)}, s.sequence})
            add(v);
    }
};
static void hashRows(Digest& d, const std::vector<TileState>& rows) {
    d.add(rows.size());
    for (const auto& row : rows) d.add(row);
}

// splitmix64 with plain modulo: unlike <random> distributions, identical on every standard library.
struct Rng {
    uint64_t state;
    uint64_t next() {
        uint64_t z = (state += 0x9e3779b97f4a7c15ull);
        z = (z ^ (z >> 30)) * 0xbf58476d1ce4e5b9ull;
        z = (z ^ (z >> 27)) * 0x94d049bb133111ebull;
        return z ^ (z >> 31);
    }
    uint32_t below(uint64_t n) { return static_cast<uint32_t>(next() % n); }
};

struct Options {
    size_t n = 1'000'000, reps = 5, players = 64, rate = 5000, seconds = 3;
    uint64_t seed = 1;
    std::filesystem::path dir = "bench-data";
    std::string only;
    bool wants(std::string_view name) const {
        return only.empty() || name.starts_with(only) || std::string_view(only).starts_with(name);
    }
    // Queries whose cost grows with history length run fewer times on long histories.
    size_t heavy() const { return std::clamp<size_t>(10'000'000 / n, 5, 200); }
};

// Map layout: 16 hot 32x32 zones and uniform noise below 512x512; x 600..663 is touched only during
// the first 1% of the session; x 800.. and 840.. hold the griefing tail; x >= 1000 is never touched.
struct Fixture {
    static constexpr int kGrief = 16;  // 16x16 positions per griefed region
    static constexpr size_t kTail = 3 * kGrief * kGrief;
    std::vector<TileState> states;
    std::vector<std::string> uuids;
    std::vector<Pos> recent, deep, miss;
    Rect hot{0, 0, 31, 31}, fresh{800, 0, 800 + kGrief - 1, kGrief - 1},
        prior{840, 0, 840 + kGrief - 1, kGrief - 1};
    time_t_ window{};
    player_t_ griefer{}, absent{};
};

static Fixture makeFixture(const Options& o) {
    Fixture f;
    Rng rng{o.seed};
    f.griefer = o.players + 1;
    f.absent = o.players + 2;
    // Mindustry UUIDs are 12 base64 characters; index 0 is the empty actor.
    const char* alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    f.uuids.resize(o.players + 3);
    for (size_t id = 1; id < f.uuids.size(); ++id) {
        Rng name{o.seed ^ (id * 0x2545f4914f6cdd1dull)};
        for (int i = 0; i < 11; ++i) f.uuids[id] += alphabet[name.below(64)];
        f.uuids[id] += '=';
    }
    // --n counts every record, so --n 10000000 reaches the archive and session record limit.
    const size_t body = o.n - Fixture::kTail, deep_end = std::max<size_t>(body / 100, 1);
    time_t_ time = 1;
    f.states.reserve(o.n);
    for (size_t i = 0; i < body; ++i) {
        Pos pos;
        if (i < deep_end && rng.below(2) == 0) {
            pos = Pos(600 + rng.below(64), rng.below(64));
        } else if (rng.below(10) < 7) {
            auto zone = rng.below(16);
            pos = Pos(zone % 4 * 128 + rng.below(32), zone / 4 * 128 + rng.below(32));
        } else {
            pos = Pos(rng.below(512), rng.below(512));
        }
        time += rng.below(3);
        player_t_ player = 1 + rng.below(rng.below(o.players) + 1);
        unsigned team = rng.below(10) ? 1 : 2;
        auto roll = rng.below(100);
        auto action = roll < 20   ? ActionKind::BuildStarted
                      : roll < 40 ? ActionKind::BuildCompleted
                      : roll < 45 ? ActionKind::DestroyStarted
                      : roll < 55 ? ActionKind::DestroyCompleted
                      : roll < 65 ? ActionKind::Rotate
                      : roll < 95 ? ActionKind::Configure
                      : roll < 98 ? ActionKind::Pickup
                                  : ActionKind::Drop;
        unsigned block = 1 + rng.below(300), rotation = rng.below(4), type = 0;
        uint32_t config = 0;
        if (action == ActionKind::Configure) {
            // Type 6 configs reference a pool of 64 payloads; ingest.*.unique replaces them per record.
            type = rng.below(2) ? 1 : 6;
            config = type == 1 ? rng.below(1000) : rng.below(64);
        }
        bool destroy = action == ActionKind::DestroyCompleted || action == ActionKind::Pickup;
        TileState s(pos, player, team, true, time, block, destroy, rotation, type, config);
        s.action = action;
        f.states.push_back(s);
    }
    f.window = f.states[body - body / 100].time;
    auto tail = [&](const Rect& rect, player_t_ player, ActionKind action) {
        for (int x = rect.p1.x; x <= rect.p2.x; ++x)
            for (int y = rect.p1.y; y <= rect.p2.y; ++y) {
                TileState s(Pos(x, y), player, 1, true, ++time, 100, false, 0, 0, 0);
                s.action = action;
                f.states.push_back(s);
            }
    };
    tail(f.prior, 1, ActionKind::Baseline);
    tail(f.fresh, f.griefer, ActionKind::BuildCompleted);
    tail(f.prior, f.griefer, ActionKind::BuildCompleted);

    std::unordered_set<Pos> seen;
    for (size_t i = body; i > 0 && f.recent.size() < 1000; --i)
        if (seen.insert(f.states[i - 1].pos).second) f.recent.push_back(f.states[i - 1].pos);
    seen.clear();
    for (size_t i = 0; i < deep_end && f.deep.size() < o.heavy(); ++i)
        if (f.states[i].pos.x >= 600 && seen.insert(f.states[i].pos).second) f.deep.push_back(f.states[i].pos);
    for (size_t i = 0; i < o.heavy(); ++i) f.miss.push_back(Pos(1000 + i % 64, i / 64));
    return f;
}

static DataVec configBytes(uint64_t id, size_t size = 32) {
    DataVec bytes(size);
    Rng rng{id};
    for (size_t i = 0; i < bytes.size(); i += 8) {
        auto v = rng.next();
        std::memcpy(&bytes[i], &v, 8);
    }
    return bytes;
}

// Mirrors the JNI action path, which builds fresh uuid and payload byte vectors for every call.
static void recordAll(TileLogger& logger, const Fixture& f, bool unique) {
    for (size_t i = 0; i < f.states.size(); ++i) {
        const auto& s = f.states[i];
        const auto& uuid = f.uuids[s.player];
        DataVec payload;
        if (s.config_type >= 5) payload = configBytes(unique ? i : s.config);
        logger.Record(s.pos, DataVec(uuid.begin(), uuid.end()), s.team, s.block, s.rotation, s.config_type,
                      s.config, payload, s.action);
    }
}

struct Run {
    std::vector<int64_t> ns;
    Digest digest;
};
// Times each operation separately. Repetition 0 is warmup and alone feeds the digest.
template <class Op, class Hash>
static Run measure(const Options& o, size_t ops, Op op, Hash hash) {
    Run run;
    for (size_t r = 0; r <= o.reps; ++r)
        for (size_t i = 0; i < ops; ++i) {
            auto start = Clock::now();
            auto value = op(i);
            auto ns = since(start);
            if (r)
                run.ns.push_back(ns);
            else
                hash(run.digest, value);
        }
    return run;
}

static void result(const Options& o, const char* name, size_t ops, Run run, const char* outcome) {
    auto& ns = run.ns;
    std::sort(ns.begin(), ns.end());
    auto at = [&](size_t percent) {
        return ns.empty() ? 0ll : static_cast<long long>(ns[std::min(ns.size() - 1, ns.size() * percent / 100)]);
    };
    std::printf("result\t%s\t%zu\t%zu\t%lld\t%lld\t%lld\t%s\t%016llx\n", name, o.n, ops, at(50), at(99),
                ns.empty() ? 0ll : static_cast<long long>(ns.back()), outcome,
                static_cast<unsigned long long>(run.digest.value));
    std::fflush(stdout);
}
static void memory(const Options& o, const char* name, const Memory& m, size_t records,
                   const std::string& counters = "-") {
    std::printf("memory\t%s\t%zu\t%lld\t%lld\t%.1f\t%s\n", name, o.n, static_cast<long long>(m.live),
                static_cast<long long>(m.peak), static_cast<double>(m.live) / records, counters.c_str());
}
// Every record via the paging cursor, with its actor and configuration bytes. Quadratic in history length,
// so it runs once per scenario, outside the timed work.
static void hashLogger(Digest& d, const TileLogger& logger) {
    for (uint64_t before = UINT64_MAX;;) {
        auto rows = logger.GetHistory(Rect(0, 0, 65535, 65535), {}, -1, 0, 4097, before);
        for (const auto& s : rows) {
            d.add(s, false);
            for (uint8_t b : logger.GetPlayer(s.player)) d.add(b);
            if (s.config_type >= 5)
                for (uint8_t b : logger.GetConfig(s.config)) d.add(b);
        }
        if (rows.size() < 4097) return;
        before = rows.front().sequence;
    }
}
static std::string counters(const TileLogger& logger) {
    std::string text;
    for (int id = 2; id <= 7; ++id) text += (id > 2 ? "," : "") + std::to_string(logger.MemoryUsage(id));
    return "history/players/configs size,capacity=" + text;
}

// S1: per-record cost and retained memory of recording without disk I/O.
static void ingest(const Options& o, const Fixture& f, JournalWriter& writer) {
    const size_t records = f.states.size();
    if (o.wants("ingest.history")) {
        Run run;
        for (size_t r = 0; r <= o.reps; ++r) {
            Memory m;
            HistoryStack history;
            auto start = Clock::now();
            for (const auto& s : f.states) history.Record(s);
            auto ns = since(start) / static_cast<int64_t>(records);
            m.stop();
            if (r) {
                run.ns.push_back(ns);
                continue;
            }
            for (const auto& s : history.Records()) run.digest.add(s);
            memory(o, "ingest.history", m, records);
        }
        result(o, "ingest.history", records, run, "ok");
    }
    for (bool unique : {false, true}) {
        const char* name = unique ? "ingest.logger.unique" : "ingest.logger.repeated";
        if (!o.wants(name)) continue;
        Run run;
        for (size_t r = 0; r <= o.reps; ++r) {
            Memory m;
            auto logger = std::make_unique<TileLogger>(writer);
            logger->Start({}, "bench", "bench-content");
            auto start = Clock::now();
            recordAll(*logger, f, unique);
            auto ns = since(start) / static_cast<int64_t>(records);
            m.stop();
            if (r) {
                run.ns.push_back(ns);
                continue;
            }
            hashLogger(run.digest, *logger);
            memory(o, name, m, records, counters(*logger));
        }
        result(o, name, records, run, "ok");
    }
}

// S2-S4: read-only queries against the same history. Plan is const here, so it cannot mutate history.
static void queries(const Options& o, const Fixture& f, const HistoryStack& h) {
    const Rect all(0, 0, 65535, 65535);
    const size_t total = f.states.size();
    auto point = [&](const char* name, const std::vector<Pos>& positions) {
        if (!o.wants(name)) return;
        auto run = measure(o, positions.size(), [&](size_t i) {
            auto p = positions[i];
            return h.Last(Rect(p.x, p.y, p.x, p.y), {}, -1, 0, 1);
        }, hashRows);
        result(o, name, positions.size(), std::move(run), "ok");
    };
    point("last.recent", f.recent);
    point("last.deep", f.deep);
    point("last.miss", f.miss);

    auto page = [&](const char* name, size_t ops, const Rect& rect, std::optional<player_t_> player,
                    time_t_ cutoff, size_t size, uint64_t before) {
        if (!o.wants(name)) return;
        auto run = measure(o, ops, [&](size_t) { return h.Last(rect, player, -1, cutoff, size, before); },
                           hashRows);
        result(o, name, ops, std::move(run), "ok");
    };
    page("page.hot-zone", 1000, f.hot, {}, 0, 32, UINT64_MAX);
    // XcoreBlockHistoryProvider: one player, whole map, recent window, 4096 rows.
    page("page.window.active", 1000, all, 1, f.window, 4096, UINT64_MAX);
    page("page.window.absent", 1000, all, f.absent, f.window, 4096, UINT64_MAX);
    page("page.before.1", 1000, all, {}, 0, 32, total + 1 - total / 100);
    page("page.before.50", o.heavy(), all, {}, 0, 32, total + 1 - total / 2);
    page("page.before.99", o.heavy(), all, {}, 0, 32, total + 1 - total * 99 / 100);

    struct Planned {
        std::vector<RollbackChange> changes;
        std::string error;
    };
    auto plan = [&](const char* name, const Rect& rect, std::optional<player_t_> player, time_t_ cutoff) {
        if (!o.wants(name)) return;
        bool rejected = false;
        auto run = measure(o, o.heavy(), [&](size_t) {
            try {
                return Planned{h.Plan(rect, player, -1, cutoff), {}};
            } catch (const std::invalid_argument& e) {
                return Planned{{}, e.what()};
            }
        }, [&](Digest& d, const Planned& p) {
            rejected |= !p.error.empty();
            d.add(p.error);
            d.add(p.changes.size());
            for (const auto& c : p.changes) {
                d.add(c.before);
                d.add(c.after);
                for (auto [sequence, valid] : c.validity) d.add(sequence * 2 + valid);
            }
        });
        result(o, name, o.heavy(), std::move(run), rejected ? "rejected" : "ok");
    };
    plan("plan.miss-1x1", Rect(1000, 0, 1000, 0), {}, 0);
    // An admin undoing everything in a busy zone during the last 1% of the session.
    plan("plan.hot-zone", f.hot, {}, f.window);
    plan("plan.wide-other-player", all, f.absent, 0);
    plan("plan.wide-all", all, {}, 0);
}

// S5: committing every position of a plan. "fresh" positions have no earlier snapshot, so each
// Apply rebuilds the latest-state cache by scanning the whole history; "prior" ones stop near the tail.
static void commits(const Options& o, const Fixture& f, HistoryStack& h) {
    for (bool fresh : {true, false}) {
        const char* name = fresh ? "commit.fresh" : "commit.prior";
        if (!o.wants(name)) continue;
        Run run;
        std::optional<uint64_t> expected;
        size_t ops = 0;
        for (size_t r = 0; r <= o.reps; ++r) {
            auto changes = h.Plan(fresh ? f.fresh : f.prior, f.griefer, -1, 0);
            Digest planned;
            for (const auto& c : changes)
                for (auto [sequence, valid] : c.validity) planned.add(sequence * 2 + valid);
            if (expected && *expected != planned.value)
                throw std::runtime_error("Commit benchmark did not restore its fixture");
            expected = planned.value;
            ops = changes.size();
            for (const auto& change : changes) {
                auto start = Clock::now();
                h.Apply(change);
                if (r) run.ns.push_back(since(start));
            }
            if (!r)
                for (const auto& c : changes) {
                    auto current = h.Current(c.after.pos);
                    run.digest.add(current ? current->sequence : 0);
                }
            // Plan records only flipped entries, so flipping them back restores the fixture untimed.
            for (auto change : changes) {
                for (auto& entry : change.validity) entry.second = !entry.second;
                h.Apply(change);
            }
        }
        result(o, name, ops, std::move(run), "ok");
    }
}

// S8: loading an archive. "rollback" adds commit frames whose replay runs Apply during Load.
static void load(const Options& o, const Fixture& f, JournalWriter& writer) {
    for (bool rollback : {false, true}) {
        const char* name = rollback ? "load.rollback" : "load.plain";
        if (!o.wants(name)) continue;
        auto path = o.dir / (std::string(name) + "-" + std::to_string(o.n) + ".history");
        std::filesystem::create_directories(o.dir);
        std::filesystem::remove(path);
        {
            // Fixture only: an unbounded queue so building never trips admission control.
            JournalWriter building(size_t{1} << 40, SIZE_MAX);
            TileLogger logger(building);
            logger.Start(path, "bench", "bench-content");
            recordAll(logger, f, false);
            if (rollback) {
                const auto& uuid = f.uuids[f.griefer];
                for (const auto& s : logger.PlanRollback(f.fresh, DataVec(uuid.begin(), uuid.end()), -1, 0))
                    logger.Commit(s.plan, s.pos);
            }
            building.Flush();
            logger.CheckWritable();
        }
        auto bytes = std::filesystem::file_size(path);
        std::printf("archive\t%s\t%zu\t%ju\t%.1f\n", name, o.n, static_cast<uintmax_t>(bytes),
                    static_cast<double>(bytes) / f.states.size());
        Run run;
        const char* outcome = "ok";
        for (size_t r = 0; r <= o.reps; ++r) {
            Memory m;
            TileLogger logger(writer);
            auto start = Clock::now();
            try {
                logger.Load(path);
            } catch (const std::runtime_error& e) {
                std::printf("error\t%s\t%s\n", name, e.what());
                outcome = "rejected";
                break;
            }
            auto ns = since(start);
            m.stop();
            if (r) {
                run.ns.push_back(ns);
                continue;
            }
            hashLogger(run.digest, logger);
            memory(o, name, m, f.states.size(), counters(logger));
        }
        result(o, name, 1, std::move(run), outcome);
        std::filesystem::remove(path);
    }
}

static void metric(const Options& o, const char* name, const char* key, double value) {
    std::printf("metric\t%s\t%zu\t%s\t%.3f\n", name, o.n, key, value);
}

struct Durability {
    int64_t ns;
    uint64_t accepted, durable;
};
// Empty once the session has failed, for example after queue overflow.
static std::optional<Durability> durability(const TileLogger& logger, Clock::time_point start) {
    unsigned long long accepted, durable;
    if (std::sscanf(logger.Status().c_str(), "accepted=%llu durable=%llu", &accepted, &durable) != 2) return {};
    return Durability{since(start), accepted, durable};
}

// S6/S7: journaling to files under --dir. Producer latency is the Record call on the game thread; durability
// is how far the fsync'd operation count trails the accepted one. Uses production queue limits.
static void journal(const Options& o, const Fixture& f) {
    struct Case {
        const char* name;
        size_t limit, payload;
        bool paced;
    };
    const Case cases[] = {
        // --rate records per second, submitted in 60 Hz game ticks.
        {"journal.sustained", o.rate * o.seconds, 0, true},
        // As fast as possible until the limit or until admission control rejects a record.
        {"journal.burst.small", 200'000, 0, false},
        {"journal.burst.large", 64, max_config_bytes, false},
    };
    bool warned = false;
    for (const auto& c : cases) {
        if (!o.wants(c.name)) continue;
        std::filesystem::create_directories(o.dir);
        struct statfs fs{};
        if (!warned && statfs(o.dir.c_str(), &fs) == 0 && fs.f_type == 0x01021994) {
            std::printf("warning\tjournal\t%s is tmpfs, where fsync does nothing\n", o.dir.c_str());
            warned = true;
        }
        Run run;
        std::set<std::string> outcomes;
        auto path = o.dir / (std::string(c.name) + ".history");
        for (size_t r = 0; r <= o.reps; ++r) {
            std::filesystem::remove(path);
            std::vector<Durability> samples;
            size_t admitted = 0;
            bool overflow = false, drained = false;
            int64_t produced = 0;
            {
                JournalWriter writer;
                TileLogger logger(writer);
                logger.Start(path, "bench", "bench-content");
                auto start = Clock::now(), next_sample = start;
                // At most once per millisecond and never inside a timed Record call.
                auto sample = [&] {
                    auto now = Clock::now();
                    if (now < next_sample) return;
                    next_sample = now + std::chrono::milliseconds(1);
                    if (auto d = durability(logger, start)) samples.push_back(*d);
                };
                for (size_t tick = 0; admitted < c.limit && !overflow; ++tick) {
                    if (c.paced) {
                        auto deadline = start + std::chrono::microseconds(tick * 1'000'000 / 60);
                        while (Clock::now() < deadline) {
                            sample();
                            std::this_thread::sleep_until(std::min(deadline, next_sample));
                        }
                    }
                    // Cumulative quota, so rates below 60 or not divisible by 60 stay exact.
                    const size_t quota = c.paced ? std::min(c.limit, (tick + 1) * o.rate / 60) : c.limit;
                    while (admitted < quota) {
                        const auto& s = f.states[admitted % f.states.size()];
                        const auto& uuid = f.uuids[s.player];
                        DataVec actor(uuid.begin(), uuid.end());
                        // Large payloads are unique, so every record carries a new definition.
                        unsigned type = c.payload ? 6 : s.config_type;
                        DataVec payload = c.payload ? configBytes(admitted, c.payload)
                                          : type >= 5 ? configBytes(s.config) : DataVec{};
                        auto t = Clock::now();
                        try {
                            logger.Record(s.pos, actor, s.team, s.block, s.rotation, type, s.config, payload, s.action);
                        } catch (const std::runtime_error& e) {
                            if (!std::string_view(e.what()).starts_with("Journal queue full")) throw;
                            overflow = true;
                            break;
                        }
                        if (r) run.ns.push_back(since(t));
                        ++admitted;
                        sample();
                    }
                }
                produced = since(start);
                // The writer syncs at least every 250 ms, so a healthy session drains well within this bound.
                while (!overflow) {
                    auto d = durability(logger, start);
                    if (!d || d->ns - produced > 30'000'000'000) break;
                    samples.push_back(*d);
                    if (d->durable >= d->accepted) {
                        drained = true;
                        break;
                    }
                    std::this_thread::sleep_for(std::chrono::milliseconds(1));
                }
            }
            const char* outcome = overflow ? "overflow" : drained ? "ok" : "stalled";
            if (!r) {
                // The archive written during warmup, read back untimed, is the comparable result.
                if (drained) {
                    JournalWriter idle;
                    TileLogger loaded(idle);
                    loaded.Load(path);
                    hashLogger(run.digest, loaded);
                }
                continue;
            }
            outcomes.insert(outcome);
            // Lag: time until the durable count reaches what was accepted at each sample.
            int64_t lag = 0;
            uint64_t unsynced = 0;
            for (size_t k = 0, j = 0; k < samples.size(); ++k) {
                unsynced = std::max(unsynced, samples[k].accepted - samples[k].durable);
                j = std::max(j, k);
                while (j < samples.size() && samples[j].durable < samples[k].accepted) ++j;
                if (j < samples.size()) lag = std::max(lag, samples[j].ns - samples[k].ns);
            }
            metric(o, c.name, "admitted", admitted);
            // A failed session no longer reports its counters, so overflow runs have no durability metrics.
            if (drained) {
                metric(o, c.name, "max_unsynced_ops", unsynced);
                metric(o, c.name, "max_durability_lag_ms", lag / 1e6);
                metric(o, c.name, "drain_ms", (samples.back().ns - produced) / 1e6);
                metric(o, c.name, "durable_ops_per_s", samples.back().durable * 1e9 / samples.back().ns);
            }
            metric(o, c.name, "bytes_per_record", static_cast<double>(std::filesystem::file_size(path)) / admitted);
        }
        std::filesystem::remove(path);
        // Mixed outcomes such as "ok+overflow" stay visible instead of collapsing into one.
        std::string outcome;
        for (const auto& value : outcomes) outcome += (outcome.empty() ? "" : "+") + value;
        size_t ops = run.ns.size() / o.reps;
        result(o, c.name, ops, std::move(run), outcome.c_str());
    }
}

static long long peakResidentKiB() {
    std::ifstream status("/proc/self/status");
    for (std::string line; std::getline(status, line);)
        if (line.starts_with("VmHWM:")) return std::stoll(line.substr(6));
    return -1;
}

int main(int argc, char** argv) {
    Options o;
    for (int i = 1; i < argc; ++i) {
        std::string_view arg = argv[i];
        if (i + 1 >= argc) {
            std::fprintf(stderr, "usage: bench [--n N] [--reps R] [--seed S] [--players P] [--dir D] [--only PREFIX]"
                         " [--rate RECORDS_PER_S] [--seconds S]\n");
            return 2;
        }
        std::string value = argv[++i];
        if (arg == "--n") o.n = std::stoull(value);
        else if (arg == "--reps") o.reps = std::stoull(value);
        else if (arg == "--seed") o.seed = std::stoull(value);
        else if (arg == "--players") o.players = std::stoull(value);
        else if (arg == "--dir") o.dir = value;
        else if (arg == "--only") o.only = value;
        else if (arg == "--rate") o.rate = std::stoull(value);
        else if (arg == "--seconds") o.seconds = std::stoull(value);
        else {
            std::fprintf(stderr, "unknown option %s\n", argv[i - 1]);
            return 2;
        }
    }
    if (o.n < 10000 || o.n > max_records || o.reps < 1 || o.players < 1 || o.rate < 1 || o.seconds < 1) {
        std::fprintf(stderr, "need 10000 <= n <= %zu, and reps, players, rate, seconds >= 1\n", max_records);
        return 2;
    }
    std::printf("meta\tharness=%d\tcompiler=%s\tsizeof(TileState)=%zu\tn=%zu\treps=%zu\tseed=%llu\tplayers=%zu\n",
                kHarnessVersion, __VERSION__, sizeof(TileState), o.n, o.reps,
                static_cast<unsigned long long>(o.seed), o.players);
    try {
        auto f = makeFixture(o);
        JournalWriter writer;
        ingest(o, f, writer);
        if (o.wants("last.") || o.wants("page.") || o.wants("plan.") || o.wants("commit.")) {
            HistoryStack history;
            for (const auto& s : f.states) history.Record(s);
            queries(o, f, history);
            commits(o, f, history);
        }
        load(o, f, writer);
        journal(o, f);
    } catch (const std::exception& e) {
        std::printf("error\tbench\t%s\n", e.what());
        return 1;
    }
    std::printf("meta\tvmhwm_kib=%lld\n", peakResidentKiB());
    return 0;
}
