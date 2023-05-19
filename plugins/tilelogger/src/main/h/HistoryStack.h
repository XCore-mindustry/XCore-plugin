#pragma once
#include <vector>
#include <optional>
#include <unordered_map>
#include "TileState.h"

class HistoryStack {
public:
    void Record(const TileState& state) {
        if (std::optional<TileState> last_valid = LastValid(state.pos); last_valid && state.BlockEquals(*last_valid))
            return; // don't stack the same states
        stack_.push_back(state);
        last_valid_cache_[state.pos] = static_cast<stack_counter_t_>(stack_.size() - 1);
    }

    std::vector<TileState> Last(const Rect& rect, const std::optional<player_t_>& player, int teams, time_t_ time, size_t size) const {
        std::vector<TileState> ret;
        for(auto it = stack_.rbegin(); it != stack_.rend(); it++) {
            if (!rect.contains(it->pos))
                continue; // wrong pos
            if (player && *player != it->player)
                continue; // wrong player
            if (!(1 << it->team & teams))
                continue; // team ignored
            if (it->time < time)
                break;
            ret.push_back(*it);
            if (--size == 0)
                break;
        }
        return ret;
    }

    enum RollbackFlags {
        kPreview = 1 << 0,
    };
    std::vector<TileState> Rollback(const Rect& rect, const std::optional<player_t_>& player, int teams, time_t_ time, RollbackFlags flags) {
        struct Tile {
            bool rollback = false;
            TileState* ret = nullptr;
        };
        std::unordered_map<Pos, Tile> map;

        for(auto it = stack_.rbegin(); it != stack_.rend(); it++) {
            if (!rect.contains(it->pos))
                continue; // wrong pos
            auto state = map.find(it->pos);
            Tile* tile = state == map.end() ? nullptr : &state->second;
            if (!tile) {
                bool backward = time < it->time; 
                bool forward = !it->valid; 
                bool sel_player = !player || it->player == player; 
                bool sel_team = 1 << it->team & teams;
                printf("back: %d, forw %d, player %d, team %d\n",backward,forward,sel_player,sel_team);
                bool rollback = sel_player && sel_team && (backward || forward);
                if (!rollback && !it->valid)
                    continue;
                tile = &map[it->pos];
                tile->rollback = rollback;
            }
            if (tile->rollback) {
                if (it->valid && player && it->player != *player || it->time <= time) {
                    if (!tile->ret) {
                        tile->ret = &*it; // rollback to first suitable state
                        last_valid_cache_[it->pos] = static_cast<stack_counter_t_>(it - stack_.rbegin());
                    }
                    if (it->valid) {
                        tile->rollback = false; // terminate stack unwinding if a suitable valid state is found
                    }
                }
                if (~flags & kPreview)
                    it->valid = !!tile->ret; // invalidate or revalidate tile state
            }
        }
        std::vector<TileState> states_remove;
        std::vector<TileState> states_add;
        for (const auto& [k,v] : map) {
            if (v.ret)
                (v.ret->block ? states_add : states_remove).push_back(*v.ret);
            else if (v.rollback)
                states_remove.push_back(TileState(k));
        }
        states_remove.insert(states_remove.end(), states_add.begin(), states_add.end());
        return states_remove;
    }

    size_t Size() const noexcept {
        return stack_.size() * sizeof(stack_[0]);
    }

    size_t Capacity() const noexcept {
        return stack_.capacity() * sizeof(stack_[0]);
    }

private:
    std::optional<TileState> LastValid(const Pos& pos) const {
        auto it = last_valid_cache_.find(pos);
        if (it != last_valid_cache_.end())
            return stack_[it->second];
        return std::nullopt;
    }

    std::vector<TileState> stack_;
    std::unordered_map<Pos, stack_counter_t_> last_valid_cache_;
};