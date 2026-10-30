#pragma once
#include <array>
#include <chrono>
#include <fstream>
#include <memory>
#include <unordered_map>
#include <unordered_set>
#include "HistoryStack.h"
#include "Cache.h"
#include "Journal.h"

class TileLogger {
public:
    explicit TileLogger(JournalWriter& writer) : writer_(writer) { players_[DataVec{}]; }
    void Start(const std::filesystem::path& path, const std::string& session, const std::string& manifest) {
        if (session.empty() || session.size() > 128 || manifest.size() > 512)
            throw std::invalid_argument("Invalid session metadata");
        session_ = session;
        manifest_ = manifest;
        begin_ = std::chrono::steady_clock::now();
        started_ = std::chrono::duration_cast<std::chrono::seconds>(
                       std::chrono::system_clock::now().time_since_epoch())
                       .count();
        if (!path.empty()) {
            journal_ = std::make_shared<JournalState>();
            journal_->path = path;
            Encoder header;
            header.integer(0, 1);
            header.integer(started_, 8);
            header.text(session_);
            header.text(manifest_);
            auto bytes = frame(header.bytes);
            bytes.insert(bytes.begin(), magic_.begin(), magic_.end());
            writer_.Enqueue(journal_, std::move(bytes), true);
        }
    }
    time_t_ Duration() const {
        if (read_only_) return elapsed_;
        return elapsed_ +
               std::chrono::duration_cast<std::chrono::seconds>(std::chrono::steady_clock::now() - begin_)
                   .count();
    }
    const std::string& Session() const { return session_; }
    bool ReadOnly() const { return read_only_; }
    bool Legacy() const { return legacy_; }
    const std::string& Manifest() const { return manifest_; }
    std::optional<TileState> Current(Pos pos) const { return history_.Current(pos); }
    void CheckWritable() const {
        if (read_only_)
            throw std::runtime_error("Archive is read-only; start a live session before changing the world");
        if (journal_) journal_->Check();
    }
    std::string Status() const {
        if (read_only_)
            return legacy_      ? "legacy archive (read-only, 16-bit clock)"
                   : recovered_ ? "archive (read-only, incomplete tail ignored)"
                                : "archive (read-only)";
        if (!journal_) return "memory only";
        if (journal_->failed) {
            std::lock_guard lock(journal_->mutex);
            return "FAILED: " + journal_->error;
        }
        return "accepted=" + std::to_string(journal_->accepted.load()) +
               " durable=" + std::to_string(journal_->durable.load()) +
               " queuedBytes=" + std::to_string(journal_->queued.load());
    }
    const DataVec& GetPlayer(player_t_ player) const { return players_[player]; }
    const DataVec& GetConfig(config_t_ config) const { return configs_[config]; }

    void Record(Pos pos, const DataVec& uuid, unsigned team, unsigned block, unsigned rotation, unsigned type,
                uint32_t scalar, const DataVec& payload, ActionKind action) {
        CheckWritable();
        if (uuid.size() > 256 || team > 255 || block > 65535 || rotation > 3 || type > 9 ||
            payload.size() > max_config_bytes ||
            (action > ActionKind::AdminFill && action != ActionKind::Legacy))
            throw std::invalid_argument("Invalid tile action");
        if (history_.Records().size() >= max_records)
            throw std::runtime_error("Session record limit reached");
        TileState state(pos, players_[uuid], team, true, Duration(), block,
                        action == ActionKind::DestroyCompleted || action == ActionKind::Pickup || !block,
                        rotation, type, type >= 5 ? configs_[payload] : scalar);
        state.action = action;
        auto prepared = history_.PrepareRecord(state);
        if (!prepared) return;
        Encoder out;
        out.integer(1, 1);
        EncodeState(out, *prepared);
        Append(out.bytes);
        history_.Restore(*prepared);
        if (prepared->config_type >= 5) serialized_configs_.insert(prepared->config);
        ++revision_;
        plans_.clear();
    }
    std::vector<TileState> GetHistory(const Rect& rect, const DataVec& uuid, int teams, int64_t time,
                                      size_t size, uint64_t before = UINT64_MAX) const {
        if (size < 1 || size > 4097) throw std::invalid_argument("History limit must be 1..4097");
        auto player = uuid.empty() ? std::optional<player_t_>{} : players_.at(uuid);
        if (!uuid.empty() && !player) return {};
        return history_.Last(rect, player, teams, AbsTime(time), size, before);
    }
    std::vector<TileState> PlanRollback(const Rect& rect, const DataVec& uuid, int teams, int64_t time) {
        CheckWritable();
        auto player = uuid.empty() ? std::optional<player_t_>{} : players_.at(uuid);
        if (!uuid.empty() && !player) return {};
        auto changes = history_.Plan(rect, player, teams, AbsTime(time));
        if (plans_.size() >= 16) plans_.clear();
        uint64_t id = ++next_plan_;
        Plan plan;
        plan.revision = revision_;
        std::vector<TileState> result;
        size_t restore_bytes = 0;
        for (auto& change : changes) {
            for (const TileState* state : {&change.before, &change.after})
                if (state->config_type >= 5) restore_bytes += configs_[state->config].size();
            if (restore_bytes > 4 * 1024 * 1024)
                throw std::invalid_argument("Rollback configurations exceed 4 MiB; select a smaller area");
            change.after.plan = id;
            change.after.expected = change.before.sequence;
            result.push_back(change.after);
            plan.changes.emplace(change.after.pos, std::move(change));
        }
        if (!result.empty()) plans_.emplace(id, std::move(plan));
        return result;
    }
    void Commit(uint64_t id, Pos pos) {
        CheckWritable();
        auto plan = plans_.find(id);
        if (plan == plans_.end() || plan->second.revision != revision_)
            throw std::runtime_error("Rollback plan expired");
        auto change = plan->second.changes.find(pos);
        if (change == plan->second.changes.end()) throw std::runtime_error("Unknown rollback position");
        auto current = history_.Current(pos);
        if ((current ? current->sequence : 0) != change->second.before.sequence)
            throw std::runtime_error("Rollback position changed");
        Encoder out;
        out.integer(2, 1);
        out.integer(pos.x, 2);
        out.integer(pos.y, 2);
        out.integer(change->second.validity.size(), 4);
        for (auto [sequence, valid] : change->second.validity) {
            out.integer(sequence, 8);
            out.integer(valid, 1);
        }
        Append(out.bytes);
        history_.Apply(change->second);
        plan->second.changes.erase(change);
        plan->second.revision = ++revision_;
    }
    void Discard(uint64_t id) { plans_.erase(id); }
    size_t MemoryUsage(int id) const {
        switch (id) {
            case 2:
                return history_.Size();
            case 3:
                return history_.Capacity();
            case 4:
                return players_.Size();
            case 5:
                return players_.Capacity();
            case 6:
                return configs_.Size();
            case 7:
                return configs_.Capacity();
            default:
                throw std::invalid_argument("Unknown memory counter");
        }
    }
    // Called on an archive-reader worker, never against the active store.
    void Load(const std::filesystem::path& path) {
        std::ifstream file(path, std::ios::binary);
        if (!file) throw std::runtime_error("Cannot open archive: " + pathUtf8(path));
        if (std::filesystem::file_size(path) > 1024ull * 1024 * 1024)
            throw std::runtime_error("Archive exceeds 1 GiB reader limit");
        DataVec data(std::istreambuf_iterator<char>(file), {});
        read_only_ = true;
        if (data.size() >= magic_.size() && std::equal(magic_.begin(), magic_.end(), data.begin())) {
            LoadV2(data);
        } else
            LoadLegacy(path, data);
        if (!history_.Records().empty()) elapsed_ = history_.Records().back().time;
    }

private:
    struct Plan {
        uint64_t revision{};
        std::unordered_map<Pos, RollbackChange> changes;
    };
    time_t_ AbsTime(int64_t time) const {
        if (time >= 0) return static_cast<time_t_>(time);
        uint64_t ago = static_cast<uint64_t>(-(time + 1)) + 1;
        auto now = Duration();
        return ago >= now ? 0 : now - ago;
    }
    void Append(const DataVec& bytes) {
        if (journal_) writer_.Enqueue(journal_, frame(bytes));
    }
    void EncodeState(Encoder& out, const TileState& s) const {
        out.integer(s.sequence, 8);
        out.integer(s.time, 8);
        out.integer(s.pos.x, 2);
        out.integer(s.pos.y, 2);
        out.integer(s.team, 2);
        out.integer(s.block, 2);
        out.integer(s.valid, 1);
        out.integer(s.destroy, 1);
        out.integer(s.rotation, 1);
        out.integer(s.config_type, 1);
        out.integer(static_cast<uint8_t>(s.action), 1);
        out.integer(s.config, 4);
        out.blob(players_[s.player]);
        bool definition = s.config_type >= 5 && !serialized_configs_.contains(s.config);
        out.integer(definition, 1);
        if (definition) out.blob(configs_[s.config]);
    }
    TileState DecodeState(Decoder& in) {
        TileState s;
        s.sequence = in.integer(8);
        s.time = in.integer(8);
        s.pos.x = in.integer(2);
        s.pos.y = in.integer(2);
        s.team = in.integer(2);
        s.block = in.integer(2);
        auto valid = in.integer(1), destroy = in.integer(1);
        s.valid = valid;
        s.destroy = destroy;
        s.rotation = in.integer(1);
        s.config_type = in.integer(1);
        s.action = static_cast<ActionKind>(in.integer(1));
        s.config = in.integer(4);
        if (s.time > INT64_MAX || s.team > 255 || valid > 1 || destroy > 1 || s.rotation > 3 ||
            s.config_type > 9 || (s.action > ActionKind::AdminFill && s.action != ActionKind::Legacy))
            throw std::runtime_error("Invalid record fields");
        s.player = players_[in.blob(256)];
        auto definition = in.integer(1);
        if (definition > 1 || (s.config_type < 5 && definition))
            throw std::runtime_error("Invalid configuration definition");
        if (s.config_type >= 5) {
            uint32_t file_id = s.config;
            if (definition) {
                if (loaded_configs_.contains(file_id))
                    throw std::runtime_error("Duplicate configuration definition");
                loaded_configs_[file_id] = configs_[in.blob()];
            }
            auto found = loaded_configs_.find(file_id);
            if (found == loaded_configs_.end()) throw std::runtime_error("Undefined configuration reference");
            s.config = found->second;
        }
        if (!history_.Records().empty() && s.time < history_.Records().back().time)
            throw std::runtime_error("Non-monotonic journal clock");
        return s;
    }
    void LoadV2(const DataVec& bytes) {
        Decoder stream{bytes, magic_.size()};
        bool header = false;
        while (stream.remaining()) {
            if (stream.remaining() < 8) {
                recovered_ = true;
                break;
            }
            size_t length = stream.integer(4);
            uint32_t hash = stream.integer(4);
            if (!length || length > max_frame_bytes)
                throw std::runtime_error("Invalid journal frame length at " + std::to_string(stream.offset));
            if (length > stream.remaining()) {
                recovered_ = true;
                break;
            }
            DataVec body(bytes.begin() + stream.offset, bytes.begin() + stream.offset + length);
            stream.offset += length;
            if (checksum(body) != hash)
                throw std::runtime_error("Journal checksum mismatch at " +
                                         std::to_string(stream.offset - length));
            Decoder in{body};
            auto type = in.integer(1);
            if (!header) {
                if (type != 0) throw std::runtime_error("Missing journal header");
                started_ = in.integer(8);
                session_ = in.text(128);
                manifest_ = in.text(512);
                if (session_.empty()) throw std::runtime_error("Missing archive session");
                header = true;
            } else if (type == 1) {
                if (history_.Records().size() >= max_records)
                    throw std::runtime_error("Archive record limit exceeded");
                history_.Restore(DecodeState(in));
            } else if (type == 2) {
                RollbackChange change;
                auto x = in.integer(2), y = in.integer(2);
                change.after.pos = Pos(x, y);
                size_t count = in.integer(4);
                if (count > max_records || count > in.remaining() / 9)
                    throw std::runtime_error("Invalid rollback count");
                for (size_t i = 0; i < count; ++i) {
                    uint64_t sequence = in.integer(8), valid = in.integer(1);
                    if (valid > 1) throw std::runtime_error("Invalid rollback validity");
                    change.validity.emplace_back(sequence, valid);
                }
                history_.Apply(change);
            } else
                throw std::runtime_error("Unknown journal operation");
            in.end();
        }
        if (!header) throw std::runtime_error("Truncated journal header");
    }
    void LoadLegacy(std::filesystem::path path, const DataVec& data) {
        const DataVec header{'T', 'L', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        if (data.size() < 14 || data.size() % 14 || !std::equal(header.begin(), header.end(), data.begin()))
            throw std::runtime_error("Invalid legacy archive header/length");
        players_ = {};
        configs_ = {};
        players_.LoadLegacy(path.replace_extension("players"), 256);
        configs_.LoadLegacy(path.replace_extension("configs"), max_config_bytes);
        BitStack input;
        input.buffer_ = data;
        input.read_i_ = 14 * 8;
        while (input.read_i_ / 8 < data.size()) {
            if (history_.Records().size() >= max_records)
                throw std::runtime_error("Legacy record limit exceeded");
            TileState s;
            auto x = input.read(16), y = input.read(16);
            s.pos = Pos(x, y);
            s.player = input.read(12);
            s.team = input.read(3);
            s.valid = input.read(1);
            s.time = input.read(16);
            s.block = input.read(10);
            s.destroy = input.read(1);
            s.rotation = input.read(2);
            s.config_type = input.read(3);
            s.config = input.read(32);
            players_[s.player];
            if (s.config_type >= 5) configs_[s.config];
            s.sequence = history_.Records().size() + 1;
            history_.Restore(s);
        }
        // Empty legacy stores may have an empty player dictionary.
        players_[DataVec{}];
        legacy_ = true;
        session_ = "legacy:" + pathUtf8(path.stem());
    }
    static constexpr std::array<uint8_t, 8> magic_{'T', 'L', 'J', 2, 13, 10, 26, 0};
    JournalWriter& writer_;
    HistoryStack history_;
    Cache<player_t_> players_;
    Cache<config_t_> configs_;
    std::unordered_set<config_t_> serialized_configs_;
    std::unordered_map<uint32_t, config_t_> loaded_configs_;
    std::shared_ptr<JournalState> journal_;
    std::string session_, manifest_;
    time_t_ elapsed_{};
    uint64_t started_{}, revision_{};
    inline static uint64_t next_plan_{};
    std::chrono::steady_clock::time_point begin_{std::chrono::steady_clock::now()};
    bool read_only_{}, legacy_{}, recovered_{};
    std::unordered_map<uint64_t, Plan> plans_;
};
