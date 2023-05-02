#pragma once
#include <vector>
#include <optional>
#include "TileState.h"

class TileHistory {
public:
    void Record(const TileState& state) {
        if (std::optional<TileState> last = LastValid(); last && state.BlockRotationConfigEquals(*last))
            return; // don't stack the same states
        states_.push_back(state);
    }

    std::vector<TileState> Last(const std::optional<player_t_>& player, int teams, time_t_ time, size_t size) const {
        std::vector<TileState> ret;
        for(auto it = states_.rbegin(); it != states_.rend(); it++) {
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
    std::optional<TileState> Rollback(const std::optional<player_t_>& player, int teams, time_t_ time, RollbackFlags flags) {
        if (states_.empty())
            return std::nullopt; // nothing to rollback
        if (states_.rbegin()->valid) {
            if (player && *player != states_.rbegin()->player)
                return std::nullopt; // the last change was not made by this player
            if (time >= states_.rbegin()->time)
                return std::nullopt; // the last change was made too long ago
            if (!(1 << states_.rbegin()->team & teams))
                return std::nullopt; // team ignored
        }

        TileState* ret = nullptr;
        for(auto it = states_.rbegin(); it != states_.rend(); it++) {
            if (player && *player != it->player || time >= it->time) {
                if (!ret)
                    ret = &*it; // save the first suitable state
                if (it->valid)
                    break; // terminate stack unwinding if a suitable valid state is found
            }
            if (~flags & kPreview)
                it->valid = !!ret; // invalidate or revalidate tile state
        }
        return ret ? *ret : TileState();
    }

    size_t Size() const noexcept {
        return states_.size() * sizeof(states_[0]);
    }

    size_t Capacity() const noexcept {
        return states_.capacity() * sizeof(states_[0]);
    }

private:
    std::optional<TileState> LastValid() const {
        for(auto it = states_.rbegin(); it != states_.rend(); it++) {
            if (it->valid)
                return *it;
        }
        return std::nullopt;
    }

    std::vector<TileState> states_;
};