#pragma once
#include <vector>
#include <optional>
#include "TileState.h"

class TileHistory {
public:
    void Record(const TileState& state) {
        if (states_.size() && state.BlockRotationConfigEquals(*states_.rbegin()))
            return; // don't stack the same states
        states_.push_back(state);
    }

    std::vector<TileState> Last(size_t count) const {
        size_t last = states_.size();
        if (count)
            last = std::min(last, count);
        return std::vector<TileState>(states_.end() - last, states_.end());
    }

    std::optional<TileState> Rollback(const std::optional<player_t_>& player, int teams, time_t_ time, bool erase) {
        if (states_.empty())
            return std::nullopt; // nothing to rollback
        if (player && *player != states_.rbegin()->player)
            return std::nullopt; // the last change was not made by this player
        if (time >= states_.rbegin()->time)
            return std::nullopt; // the last change was made too long ago
        if (!(1 << states_.rbegin()->team & teams))
            return std::nullopt; // team ignored

        for(auto it = states_.rbegin(); it != states_.rend(); it++) {
            if (player && *player != it->player || time >= it->time) {
                TileState state = *it;
                if (erase)
                    states_.erase(it.base(), states_.end());
                return state;
            }
        }
        if (erase)
            states_.clear();
        return TileState{};
    }

    size_t Size() const noexcept {
        return states_.size() * sizeof(states_[0]);
    }

    size_t Capacity() const noexcept {
        return states_.capacity() * sizeof(states_[0]);
    }

private:
    std::vector<TileState> states_;
};