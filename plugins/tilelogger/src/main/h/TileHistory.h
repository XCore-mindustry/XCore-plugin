#pragma once
#include <vector>
#include <optional>
#include "TileState.h"

class TileHistory {
public:
    void Record(const TileState& state) {
        if (std::optional<TileState> last_valid = LastValid(); last_valid && state.BlockRotationConfigEquals(*last_valid))
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
        if (player && !RollbackRequiredPlayer(*player))
            return std::nullopt; // the last change was not made by this player
        if (!RollbackRequiredTime(time))
            return std::nullopt; // the last change was made too long ago
        if (!RollbackRequiredTeam(teams))
            return std::nullopt; // team ignored

        TileState* ret = nullptr;
        for(auto it = states_.rbegin(); it != states_.rend(); it++) {
            if (it->valid && player && it->player != *player || it->time <= time) {
                if (!ret)
                    ret = &*it; // rollback to first suitable state
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
    std::optional<TileState> Last() const {
        if (states_.empty())
            return std::nullopt;
        return *states_.rbegin();
    }

    std::optional<TileState> LastValid() const {
        for(auto it = states_.rbegin(); it != states_.rend(); it++) {
            if (it->valid)
                return *it;
        }
        return std::nullopt;
    }

    bool RollbackRequiredPlayer(player_t_ player) const {
        for(auto it = states_.rbegin(); it != states_.rend(); it++) {
            if (it->player == player)
                return true;
            if (it->valid)
                return false;
        }
        return false;
    }

    bool RollbackRequiredTime(time_t_ time) const {
        std::optional<TileState> last = Last();
        return last && (!last->valid || last->time > time);
    }

    bool RollbackRequiredTeam(int teams) const {
        std::optional<TileState> last_valid = LastValid();
        return !last_valid || 1 << last_valid->team & teams;
    }

    std::vector<TileState> states_;
};