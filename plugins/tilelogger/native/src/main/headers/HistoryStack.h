#pragma once
#include <algorithm>
#include <filesystem>
#include <optional>
#include <stdexcept>
#include <unordered_map>
#include <vector>
#include "TileState.h"

struct RollbackChange {
    TileState before, after;
    std::vector<std::pair<uint64_t, bool>> validity;
};

class HistoryStack {
public:
    enum RollbackFlags { kPreview = 1 };

    void Reset(const std::filesystem::path& path, bool) {
        if (!path.empty()) throw std::invalid_argument("Use TileLogger to open archives");
        records_.clear();
        latest_.clear();
    }
    const std::vector<TileState>& Records() const { return records_; }
    std::optional<TileState> Current(Pos pos) const {
        auto it = latest_.find(pos);
        return it == latest_.end() ? std::nullopt : std::optional(records_.at(it->second));
    }
    void Restore(TileState state) {
        if (state.sequence != records_.size() + 1) throw std::runtime_error("Invalid record sequence");
        records_.push_back(state);
        if (state.valid && state.isSnapshot()) latest_[state.pos] = records_.size() - 1;
    }
    std::optional<TileState> PrepareRecord(TileState state) const {
        if (auto last = Current(state.pos)) {
            if (state.destroy && state.config_type != 9) {
                state.block = last->block;
                state.rotation = last->rotation;
                state.config_type = last->config_type;
                state.config = last->config;
            }
            if (state.action == ActionKind::Legacy && !state.RecordAction(*last)) return {};
        }
        state.sequence = records_.size() + 1;
        return state;
    }
    void Record(TileState state) {
        if (auto prepared = PrepareRecord(state)) Restore(*prepared);
    }
    std::vector<TileState> Last(const Rect& rect, std::optional<player_t_> player, int teams, time_t_ cutoff,
                                size_t size, uint64_t before = UINT64_MAX) const {
        std::vector<TileState> result;
        if (size == 0) return result;
        for (auto it = records_.rbegin(); it != records_.rend(); ++it) {
            if (it->sequence >= before) continue;
            if (it->time < cutoff) break;
            if (!rect.contains(it->pos) || (player && *player != it->player) ||
                !SelectedTeam(it->team, teams))
                continue;
            result.push_back(*it);
            if (result.size() == size) break;
        }
        std::reverse(result.begin(), result.end());
        return result;
    }
    static bool SelectedTeam(unsigned team, int teams) {
        return teams == -1 || (team < 32 && (static_cast<uint32_t>(teams) & (uint32_t{1} << team)));
    }
    std::vector<RollbackChange> Plan(const Rect& rect, std::optional<player_t_> player, int teams,
                                     time_t_ cutoff) const {
        struct Candidate {
            bool unwind{};
            bool found{};
            bool payload{};
            RollbackChange change;
        };
        std::unordered_map<Pos, Candidate> map;
        size_t selected_positions = 0, visited = 0;
        for (auto it = records_.rbegin(); it != records_.rend(); ++it) {
            if (!it->isSnapshot() || !rect.contains(it->pos)) continue;
            if (++visited > 100000)
                throw std::invalid_argument("Rollback scan exceeds 100000 snapshots; select a smaller area");
            auto entry = map.find(it->pos);
            if (entry == map.end()) {
                bool selected = (!player || *player == it->player) && SelectedTeam(it->team, teams);
                bool unwind = selected && (cutoff < it->time ||
                                           (cutoff == 0 && it->action != ActionKind::Baseline) || !it->valid);
                if (!unwind && !it->valid) continue;
                Candidate value;
                value.unwind = unwind;
                value.change.before = Current(it->pos).value_or(TileState(it->pos));
                value.change.after = TileState(it->pos);
                value.change.after.destroy = true;
                value.change.after.team = value.change.before.team;
                entry = map.emplace(it->pos, std::move(value)).first;
                if (unwind && ++selected_positions > 2048)
                    throw std::invalid_argument("Rollback exceeds 2048 positions; select a smaller area");
            }
            auto& candidate = entry->second;
            if (!candidate.unwind) continue;
            // A tile-only plan cannot reclaim a building from a carrier or another position.
            candidate.payload |= it->action == ActionKind::Pickup || it->action == ActionKind::Drop;
            const bool legacyConstruction =
                it->action == ActionKind::Legacy && it->block >= 3 && it->block <= 11;
            bool boundary = it->time < cutoff ||
                            (it->time == cutoff && (cutoff != 0 || it->action == ActionKind::Baseline));
            if (((it->valid && player && it->player != *player) || boundary) && !legacyConstruction) {
                if (!candidate.found) {
                    candidate.change.after = *it;
                    candidate.found = true;
                }
                if (it->valid) candidate.unwind = false;
            }
            if (it->valid != candidate.found)
                candidate.change.validity.emplace_back(it->sequence, candidate.found);
        }
        std::vector<RollbackChange> result;
        for (auto& [pos, c] : map) {
            if (!c.change.validity.empty() && c.payload)
                throw std::invalid_argument("Rollback crosses a payload move");
            if (!c.change.validity.empty()) result.push_back(std::move(c.change));
        }
        if (result.size() > 2048)
            throw std::invalid_argument("Rollback exceeds 2048 buildings; select a smaller area");
        std::sort(result.begin(), result.end(), [](const auto& a, const auto& b) {
            if (a.after.destroy != b.after.destroy) return a.after.destroy;
            return a.after.pos.x == b.after.pos.x ? a.after.pos.y < b.after.pos.y
                                                  : a.after.pos.x < b.after.pos.x;
        });
        return result;
    }
    void Apply(const RollbackChange& change) {
        for (auto [sequence, valid] : change.validity) {
            if (!sequence || sequence > records_.size() || records_[sequence - 1].pos != change.after.pos)
                throw std::runtime_error("Invalid rollback journal reference");
        }
        for (auto [sequence, valid] : change.validity) records_[sequence - 1].valid = valid;
        Rebuild(change.after.pos);
    }
    std::vector<TileState> Rollback(const Rect& rect, std::optional<player_t_> player, int teams,
                                    time_t_ cutoff, RollbackFlags flags) {
        auto changes = Plan(rect, player, teams, cutoff);
        std::vector<TileState> result;
        for (const auto& change : changes) {
            result.push_back(change.after);
            if (!(flags & kPreview)) Apply(change);
        }
        return result;
    }
    size_t Size() const { return records_.size() * sizeof(TileState); }
    size_t Capacity() const { return records_.capacity() * sizeof(TileState); }

private:
    void Rebuild(Pos pos) {
        latest_.erase(pos);
        for (size_t i = records_.size(); i > 0; --i) {
            const auto& s = records_[i - 1];
            if (s.pos == pos && s.valid && s.isSnapshot()) {
                latest_[pos] = i - 1;
                return;
            }
        }
    }
    std::vector<TileState> records_;
    std::unordered_map<Pos, size_t> latest_;
};
