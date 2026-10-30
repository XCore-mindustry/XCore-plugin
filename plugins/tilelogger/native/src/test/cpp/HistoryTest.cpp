#include <algorithm>
#include <bit>
#include <chrono>
#include <cstdint>
#include <filesystem>
#include <functional>
#include <iostream>
#include <optional>
#include <random>
#include <stdexcept>
#include "HistoryStack.h"
#include "TileLogger.h"
#include "TileLoggerAbi.h"

struct AbiResult {
    TLResult value{};
    ~AbiResult() { tl_release(&value); }
};

static void check(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
}

static TileState state(uint64_t time, int block, int player = 1, int team = 1, bool destroy = false) {
    return TileState(Pos(1, 1), player, team, true, time, block, destroy, 0, 0, 0);
}

int main() {
    const Rect all(0, 0, 65535, 65535);
    int failures = 0;
    auto test = [&](const char* name, const std::function<void()>& body) {
        try {
            body();
            std::cout << "PASS " << name << '\n';
        } catch (const std::exception& e) {
            ++failures;
            std::cerr << "FAIL " << name << ": " << e.what() << '\n';
        }
    };
    test("payload moves reject the whole plan without changing history", [&] {
        HistoryStack history;
        auto baseline = state(1, 20); baseline.action = ActionKind::Baseline;
        history.Record(baseline);
        auto pickup = state(2, 20, 1, 1, true); pickup.action = ActionKind::Pickup;
        history.Record(pickup);
        auto drop = state(3, 20); drop.pos = Pos(2, 1); drop.action = ActionKind::Drop;
        history.Record(drop);
        auto edit = drop; edit.time = 4; edit.action = ActionKind::Configure;
        history.Record(edit);
        auto independent = baseline; independent.pos = Pos(3, 1);
        history.Record(independent);
        auto changed = independent; changed.time = 5; changed.block = 21;
        changed.action = ActionKind::BuildCompleted; history.Record(changed);
        for (const Rect& area : {all, Rect(1, 1, 1, 1), Rect(2, 1, 2, 1)}) {
            bool rejected = false;
            try { history.Plan(area, {}, -1, 0); }
            catch (const std::invalid_argument& e) {
                rejected = std::string(e.what()) == "Rollback crosses a payload move";
            }
            check(rejected, "payload plan accepted");
        }
        check(history.Current(Pos(1, 1))->destroy, "pickup validity changed");
        check(history.Current(Pos(3, 1))->block == 21, "unrelated history changed");
        check(history.Plan(Rect(3, 1, 3, 1), {}, -1, 0).size() == 1, "safe area blocked");
        auto later = edit; later.time = 6; later.block = 22; history.Record(later);
        check(history.Plan(Rect(2, 1, 2, 1), {}, -1, 4).size() == 1,
              "safe post-drop time range blocked");
    });
    test("zero limit is empty", [&] {
        HistoryStack history;
        history.Reset({}, false);
        history.Record(state(10, 20));
        check(history.Last(all, {}, -1, 0, 0).empty(), "zero must not underflow");
    });
    test("preview preserves subsequent destroy", [&] {
        HistoryStack history;
        history.Reset({}, false);
        history.Record(state(10, 20));
        history.Record(state(20, 21, 2));
        history.Rollback(all, {}, -1, 10, HistoryStack::kPreview);
        history.Record(state(30, 0, 2, 1, true));
        check(history.Last(all, {}, -1, 0, 1).back().block == 21, "preview changed latest state");
    });
    test("wide identifiers and clock", [&] {
        auto value = state(65536, 2048, 4096, 9);
        check(value.time == 65536, "clock truncated");
        check(value.player == 4096, "player truncated");
        check(value.team == 9, "team truncated");
        check(value.block == 2048, "block truncated");
    });
    test("team-only changes are recorded", [&] {
        HistoryStack history;
        history.Reset({}, false);
        history.Record(state(10, 20));
        history.Record(state(20, 20, 1, 2));
        check(history.Last(all, {}, -1, 0, 10).size() == 2, "team change was deduplicated");
    });
    const auto directory =
        std::filesystem::temp_directory_path() /
        ("tilelogger-test-" + std::to_string(std::chrono::steady_clock::now().time_since_epoch().count()));
    std::filesystem::create_directories(directory);
    test("journal reload rebuilds state and retains identifiers", [&] {
        JournalWriter writer;
        auto path = directory / "roundtrip.history";
        TileLogger original(writer);
        original.Start(path, "test-session", "test-content");
        original.Record(Pos(1, 1), DataVec{'u'}, 9, 2048, 3, 6, 0, DataVec{'h', 'i'},
                        ActionKind::BuildCompleted);
        original.Record(Pos(1, 1), DataVec{'u'}, 9, 0, 0, 0, 0, {}, ActionKind::DestroyCompleted);
        writer.Flush();
        TileLogger loaded(writer);
        loaded.Load(path);
        auto rows = loaded.GetHistory(all, {}, -1, 0, 10);
        check(rows.size() == 2, "reload lost records");
        check(rows.back().destroy && rows.back().block == 2048, "destroy lost block");
        check(rows.back().team == 9 && rows.back().rotation == 3, "wide data lost");
        check(loaded.GetConfig(rows.back().config) == DataVec({'h', 'i'}), "config lost");
        check(loaded.ReadOnly(), "archives must be read-only");
    });
    test("Unicode journals reopen after flush and exclusive creation preserves the archive", [&] {
        JournalWriter writer;
        auto path = directory / std::filesystem::path(u8"journal-\u0406\U0001F9F1.history");
        TileLogger store(writer);
        store.Start(path, "unicode", "content");
        store.Record(Pos(1, 1), {}, 1, 20, 0, 0, 0, {}, ActionKind::BuildCompleted);
        writer.Flush();
        store.CheckWritable();
        check(std::filesystem::exists(path), "Unicode journal not created");
        store.Record(Pos(2, 1), {}, 1, 21, 0, 0, 0, {}, ActionKind::BuildCompleted);
        writer.Flush();
        store.CheckWritable();
        auto size = std::filesystem::file_size(path);
        auto collision = std::make_shared<JournalState>();
        collision->path = path;
        writer.Enqueue(collision, DataVec{'x'}, true);
        writer.Flush();
        check(collision->write_failed, "existing Unicode journal overwritten");
        check(collision->error.find(pathUtf8(path)) != std::string::npos, "error path is not UTF-8");
        check(std::filesystem::file_size(path) == size, "exclusive creation changed the archive");
        TileLogger loaded(writer);
        loaded.Load(path);
        check(loaded.GetHistory(all, {}, -1, 0, 10).size() == 2, "reopened Unicode journal lost records");
    });
    test("incomplete tail is recovered without rewriting source", [&] {
        JournalWriter writer;
        auto path = directory / "tail.history";
        {
            TileLogger store(writer);
            store.Start(path, "tail", "content");
            writer.Flush();
        }
        {
            std::ofstream out(path, std::ios::binary | std::ios::app);
            out.put('x');
        }
        auto bytes = std::filesystem::file_size(path);
        TileLogger loaded(writer);
        loaded.Load(path);
        check(loaded.Status().find("incomplete tail") != std::string::npos, "tail recovery not reported");
        check(std::filesystem::file_size(path) == bytes, "source changed");
    });
    test("corrupt journal is rejected", [&] {
        auto path = directory / "broken.history";
        {
            std::ofstream file(path, std::ios::binary);
            file << "TL";
        }
        JournalWriter writer;
        TileLogger loaded(writer);
        bool rejected = false;
        try {
            loaded.Load(path);
        } catch (const std::exception&) {
            rejected = true;
        }
        check(rejected, "truncated header accepted");
        BitStack input;
        input.buffer_ = {1};
        rejected = false;
        try {
            input.read(32);
        } catch (const std::exception&) {
            rejected = true;
        }
        check(rejected, "legacy out-of-bounds read accepted");
    });
    test("preview does not enqueue writes", [&] {
        JournalWriter writer;
        TileLogger store(writer);
        auto path = directory / "preview.history";
        store.Start(path, "preview", "content");
        store.Record(Pos(1, 1), {}, 1, 20, 0, 0, 0, {}, ActionKind::Baseline);
        store.Record(Pos(1, 1), DataVec{'u'}, 1, 21, 0, 0, 0, {}, ActionKind::BuildCompleted);
        writer.Flush();
        auto length = std::filesystem::file_size(path);
        auto plan = store.PlanRollback(all, DataVec{'u'}, -1, 0);
        check(plan.size() == 1 && plan[0].block == 20, "first-second rollback lost baseline");
        writer.Flush();
        check(std::filesystem::file_size(path) == length, "preview wrote to disk");
    });
    test("legacy three-file archives remain readable", [&] {
        auto path = directory / std::filesystem::path(u8"legacy-\u0406\U0001F9F1.history");
        BitStack history;
        history.push_bytes(DataVec{'T', 'L', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        history.push(1, 16);
        history.push(2, 16);
        history.push(0, 12);
        history.push(1, 3);
        history.push(1, 1);
        history.push(65535, 16);
        history.push(21, 10);
        history.push(0, 1);
        history.push(3, 2);
        history.push(6, 3);
        history.push(0, 32);
        BitStack players;
        players.push(1, 32);
        players.push_bytes(DataVec{'u'});
        BitStack configs;
        configs.push(2, 32);
        configs.push_bytes(DataVec{'h', 'i'});
        auto write = [&](const char* extension, const BitStack& bytes) {
            auto file = path;
            file.replace_extension(extension);
            std::ofstream out(file, std::ios::binary);
            out.write(reinterpret_cast<const char*>(bytes.buffer_.data()), bytes.buffer_.size());
        };
        write("history", history);
        write("players", players);
        write("configs", configs);
        JournalWriter writer;
        TileLogger loaded(writer);
        loaded.Load(path);
        auto rows = loaded.GetHistory(all, {}, -1, 0, 10);
        check(loaded.Legacy() && loaded.ReadOnly() && rows.size() == 1, "legacy metadata lost");
        check(loaded.Session() == "legacy:" + pathUtf8(path.stem()), "legacy session name is not UTF-8");
        check(rows[0].time == 65535 && rows[0].pos.y == 2 && rows[0].rotation == 3,
              "legacy record decoded incorrectly");
        check(loaded.GetPlayer(rows[0].player) == DataVec{'u'}, "legacy actor lost");
        check(loaded.GetConfig(rows[0].config) == DataVec({'h', 'i'}), "legacy config lost");
    });
    test("queue overflow preserves already accepted frames", [&] {
        JournalWriter writer(128);
        auto path = directory / "bounded.history";
        TileLogger store(writer);
        store.Start(path, "bounded", "content");
        bool rejected = false;
        try {
            store.Record(Pos(1, 1), {}, 1, 20, 0, 5, 0, DataVec(256, 1), ActionKind::Configure);
        } catch (const std::exception&) {
            rejected = true;
        }
        check(rejected, "oversized queue job accepted");
        writer.Flush();
        TileLogger loaded(writer);
        loaded.Load(path);
        check(loaded.GetHistory(all, {}, -1, 0, 10).empty(), "unaccepted event was persisted");
        check(store.Status().find("FAILED") != std::string::npos, "overflow health hidden");
        rejected = false;
        try {
            store.CheckWritable();
        } catch (const std::exception&) {
            rejected = true;
        }
        check(rejected, "flush cleared failed session health");
    });
    test("archive reads remain available after journal open failure", [&] {
        JournalWriter writer;
        auto path = directory / "recovery.history";
        TileLogger store(writer);
        store.Start(path, "recovery", "content");
        writer.Flush();
        auto failed = std::make_shared<JournalState>();
        failed->path = directory;
        writer.Enqueue(failed, DataVec{'x'}, true);
        writer.Flush();
        check(failed->write_failed, "journal open unexpectedly succeeded");
        TileLogger loaded(writer);
        loaded.Load(path);
        check(loaded.ReadOnly() && loaded.Session() == "recovery", "healthy archive unavailable");
    });
    test("checksums reject complete corruption", [&] {
        JournalWriter writer;
        auto path = directory / "checksum.history";
        TileLogger store(writer);
        store.Start(path, "checksum", "content");
        writer.Flush();
        {
            std::fstream file(path, std::ios::binary | std::ios::in | std::ios::out);
            file.seekp(-1, std::ios::end);
            file.put('!');
        }
        TileLogger loaded(writer);
        bool rejected = false;
        try {
            loaded.Load(path);
        } catch (const std::exception&) {
            rejected = true;
        }
        check(rejected, "complete corrupt frame was treated as a tail");
    });
    test("random history queries match a forward reference list", [&] {
        HistoryStack history;
        std::vector<TileState> reference;
        std::mt19937 random(4271);
        for (int i = 1; i <= 5000; i++) {
            auto value = state(i, random() % 2000 + 20, random() % 5000, random() % 256);
            value.pos = Pos(random() % 64, 1);
            value.action = ActionKind::BuildCompleted;
            history.Record(value);
            value.sequence = i;
            reference.push_back(value);
        }
        for (int i = 0; i < 200; i++) {
            uint16_t x = random() % 64;
            size_t limit = random() % 30 + 1;
            time_t_ cutoff = random() % 5001;
            std::vector<uint64_t> expected;
            for (const auto& row : reference)
                if (row.pos.x == x && row.time >= cutoff) expected.push_back(row.sequence);
            if (expected.size() > limit) expected.erase(expected.begin(), expected.end() - limit);
            auto actual = history.Last(Rect(x, 1, x, 1), {}, -1, cutoff, limit);
            check(actual.size() == expected.size(), "reference query count differs");
            for (size_t j = 0; j < actual.size(); j++)
                check(actual[j].sequence == expected[j], "reference sequence differs");
        }
        auto before = history.Last(all, {}, -1, 0, 6000);
        history.Rollback(all, {}, -1, 0, HistoryStack::kPreview);
        auto after = history.Last(all, {}, -1, 0, 6000);
        for (size_t i = 0; i < before.size(); i++)
            check(before[i].valid == after[i].valid, "random preview mutated validity");
        history.Rollback(all, {}, -1, 0, static_cast<HistoryStack::RollbackFlags>(0));
        for (int x = 0; x < 64; x++) check(!history.Current(Pos(x, 1)), "undo-all left an active state");
    });
    test("repeated configuration is defined once in the same journal", [&] {
        JournalWriter writer;
        auto path = directory / "deduplicated.history";
        TileLogger store(writer);
        store.Start(path, "deduplicated", "content");
        for (int i = 0; i < 100; i++)
            store.Record(Pos(1, 1), {}, 1, 20, 0, 5, 0, DataVec(4096, 1), ActionKind::Configure);
        writer.Flush();
        check(std::filesystem::file_size(path) < 20000, "configuration repeated in every frame");
        TileLogger loaded(writer);
        loaded.Load(path);
        auto rows = loaded.GetHistory(all, {}, -1, 0, 101);
        check(rows.size() == 100, "configuration references lost records");
        check(loaded.GetConfig(rows.back().config).size() == 4096, "configuration reference lost bytes");
    });
    test("C ABI reports errors without exceptions or state replacement", [&] {
        check(tl_abi_version() == 3 && sizeof(TLResult) == 32, "wrong ABI descriptor");
        check(tl_start(nullptr, "", "session", "content") == 1, "null output accepted");
        AbiResult start;
        check(tl_start(&start.value, "", "abi-session", "content") == 0, "start failed");
        AbiResult invalid;
        check(tl_record(&invalid.value, 1, 1, nullptr, 1, 1, 20, 0, 0, 0, nullptr, 0, 3) == 1,
              "invalid byte slice accepted");
        check(invalid.value.data && invalid.value.size && invalid.value.owner, "missing owned diagnostic");
        tl_release(&invalid.value);
        check(!invalid.value.data && !invalid.value.owner && !invalid.value.size, "release did not clear result");
        AbiResult session;
        check(tl_session(&session.value) == 0, "session read failed");
        check(std::string(session.value.data, session.value.data + session.value.size) == "abi-session",
              "failure replaced the active store");
    });
    test("C ABI transfers wide records in a bounded portable buffer", [&] {
        AbiResult start;
        check(tl_start(&start.value, "", "abi-rows", "content") == 0, "start failed");
        const uint8_t actor[] = {'u', 0, 'x'}, payload[] = {'h', 'i'};
        AbiResult record;
        check(tl_record(&record.value, 65535, 65534, actor, sizeof actor, 9, 2048, 3, 6, 0,
                        payload, sizeof payload, static_cast<int32_t>(ActionKind::BuildCompleted)) == 0,
              "record failed");
        AbiResult query;
        check(tl_history(&query.value, 0, 0, -1, -1, nullptr, 0, -1, 0, 4097, INT64_MAX) == 0,
              "query failed");
        DataVec data(query.value.data, query.value.data + query.value.size);
        Decoder input{data};
        check(input.integer(4) == 1, "wrong row count");
        check(input.integer(2) == 65535 && input.integer(2) == 65534, "coordinates narrowed");
        check(input.integer(2) == 9 && input.integer(2) == 2048, "identifiers narrowed");
        check(input.integer(1) == 3 && input.integer(1) == 6, "wrong rotation/config kind");
        check(input.integer(1) == static_cast<uint8_t>(ActionKind::BuildCompleted), "wrong event");
        check(input.integer(1) == 1, "wrong flags");
        input.integer(8);
        check(input.integer(8) == 1, "wrong sequence");
        check(input.integer(8) == 0 && input.integer(8) == 0, "unexpected rollback identifiers");
        input.integer(4);
        check(input.integer(4) == sizeof payload, "wrong original config size");
        check(input.blob(256) == DataVec(actor, actor + sizeof actor), "actor byte length lost");
        check(input.blob() == DataVec(payload, payload + sizeof payload), "configuration lost");
        input.end();
    });
    test("C ABI result storage survives session replacement", [&] {
        AbiResult start;
        check(tl_start(&start.value, "", "owned-result", "content") == 0, "start failed");
        DataVec config(max_config_bytes, 42);
        AbiResult record;
        check(tl_record(&record.value, 1, 1, nullptr, 0, 1, 20, 0, 5, 0,
                        config.data(), config.size(), static_cast<int32_t>(ActionKind::Configure)) == 0,
              "large record failed");
        AbiResult result;
        check(tl_current(&result.value, 1, 1) == 0 && result.value.size > max_config_bytes,
              "full configuration not returned");
        auto before = checksum(DataVec(result.value.data, result.value.data + result.value.size));
        AbiResult replacement;
        check(tl_start(&replacement.value, "", "replacement", "content") == 0, "replacement failed");
        check(checksum(DataVec(result.value.data, result.value.data + result.value.size)) == before,
              "result borrowed bytes from the replaced store");
    });
    std::filesystem::remove_all(directory);
    std::cout << failures << " native failures\n";
    return failures == 0 ? 0 : 1;
}
